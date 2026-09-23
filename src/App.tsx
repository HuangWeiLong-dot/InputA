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
  const { markAsNewWord } = useVocabularyStore();

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

  /**
   * Core rule: clicking a word in the text files it as the deepest level, 5
   * 「生词」 — the first time only. A word that already carries a status (a
   * level the reader picked, or 已掌握) is still looked up, but left alone, so
   * the panel shows the judgement they made instead of resetting it. This also
   * covers the panel's 重试 button, which re-runs this same handler.
   * See src/utils/wordLevel.ts for the full rule.
   */
  const handleSelectWord = useCallback(
    async (word: string, sentence: string) => {
      const clean = word.trim().toLowerCase();
      if (!clean) return;

      setActiveWord(clean);
      setSurroundingSentence(sentence);
      setIsDrawerOpen(true);
      markAsNewWord(clean);

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
    [markAsNewWord],
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
        screens, so looked-up words stay visible instead of being covered. The
        reserved width is the same custom property the drawer sizes itself with
        (--drawer-width in index.css), so the two cannot drift apart.
      */}
      <div
        className={`flex-1 flex flex-col min-w-0 transition-[margin] duration-200 ease-out ${
          isDrawerOpen ? 'lg:mr-[var(--drawer-width)]' : ''
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
