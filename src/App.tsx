import React, { useCallback, useEffect, useState } from 'react';
import { useReaderStore } from './store/useReaderStore';
import { useVocabularyStore } from './store/useVocabularyStore';
import { SAMPLE_BOOKS } from './data/sampleBooks';
import { paginateText, WORDS_PER_PAGE } from './utils/tokenizer';
import { lookupWord } from './services/dictionaryApi';
import type { DictionarySource } from './services/dictionaryApi';
import type { DictionaryEntry } from './types/reader';

import { Header } from './components/Header';
import { ReaderArea } from './components/ReaderArea';
import { PaginationBar } from './components/PaginationBar';
import { DefinitionDrawer } from './components/DefinitionDrawer';
import { BookSelectorModal } from './components/BookSelectorModal';
import { VocabularyModal } from './components/VocabularyModal';
import { SentenceAnalysisModal } from './components/SentenceAnalysisModal';

export const App: React.FC = () => {
  const { currentBook, currentChapterIndex, theme, selectedSentence, setPages, setCurrentBook } =
    useReaderStore();
  const { markLearning } = useVocabularyStore();

  const [activeWord, setActiveWord] = useState<string | null>(null);
  const [surroundingSentence, setSurroundingSentence] = useState<string>('');
  const [definitionEntry, setDefinitionEntry] = useState<DictionaryEntry | null>(null);
  const [definitionSource, setDefinitionSource] = useState<DictionarySource | null>(null);
  const [isLoadingDefinition, setIsLoadingDefinition] = useState<boolean>(false);
  const [isLookupUnavailable, setIsLookupUnavailable] = useState<boolean>(false);
  const [isDrawerOpen, setIsDrawerOpen] = useState<boolean>(false);

  // Single place that syncs the theme switch with the CSS variables.
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Load the default book when the reader is opened for the first time.
  useEffect(() => {
    if (!currentBook) {
      setCurrentBook(SAMPLE_BOOKS[0]);
    }
  }, [currentBook, setCurrentBook]);

  // Re-paginate whenever the book or the chapter changes.
  useEffect(() => {
    if (!currentBook || currentBook.chapters.length === 0) {
      setPages(['']);
      return;
    }

    const chapter = currentBook.chapters[currentChapterIndex] || currentBook.chapters[0];
    setPages(paginateText(chapter.content, WORDS_PER_PAGE));
  }, [currentBook, currentChapterIndex, setPages]);

  // Core rule 6.2: clicking a word marks it as "learning" immediately.
  const handleSelectWord = useCallback(
    async (word: string, sentence: string) => {
      const clean = word.trim().toLowerCase();
      if (!clean) return;

      setActiveWord(clean);
      setSurroundingSentence(sentence);
      setIsDrawerOpen(true);
      markLearning(clean);

      setIsLoadingDefinition(true);
      setDefinitionEntry(null);
      setDefinitionSource(null);
      setIsLookupUnavailable(false);
      try {
        const result = await lookupWord(clean);
        setDefinitionEntry(result.entry);
        setDefinitionSource(result.source);
        setIsLookupUnavailable(result.unavailable);
      } finally {
        setIsLoadingDefinition(false);
      }
    },
    [markLearning],
  );

  const handleRetryLookup = useCallback(() => {
    if (activeWord) {
      void handleSelectWord(activeWord, surroundingSentence);
    }
  }, [activeWord, surroundingSentence, handleSelectWord]);

  const handleCloseDrawer = useCallback(() => {
    setIsDrawerOpen(false);
    setActiveWord(null);
  }, []);

  return (
    <div className="min-h-screen flex flex-col bg-[var(--bg-main)] text-[var(--text-main)]">
      {/*
        The reading column narrows while the definition drawer is open on wide
        screens, so looked-up words stay visible instead of being covered.
      */}
      <div
        className={`flex-1 flex flex-col min-w-0 transition-[margin] duration-200 ease-out ${
          isDrawerOpen ? 'md:mr-96' : ''
        }`}
      >
        <Header />
        <ReaderArea onSelectWord={handleSelectWord} activeWord={activeWord} />
        <PaginationBar />
      </div>

      <DefinitionDrawer
        word={activeWord}
        entry={definitionEntry}
        source={definitionSource}
        isLoading={isLoadingDefinition}
        isUnavailable={isLookupUnavailable}
        isOpen={isDrawerOpen}
        onClose={handleCloseDrawer}
        onRetry={handleRetryLookup}
        // Following "原形 run" keeps the sentence context but looks up the lemma.
        onLookupWord={(next) => void handleSelectWord(next, surroundingSentence)}
        surroundingSentence={surroundingSentence}
      />

      <BookSelectorModal />
      <VocabularyModal />
      <SentenceAnalysisModal key={selectedSentence} />
    </div>
  );
};

export default App;
