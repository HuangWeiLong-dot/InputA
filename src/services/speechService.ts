/**
 * Pronounce word or sentence using native browser SpeechSynthesis
 * with audio URL fallback.
 */

let activeAudio: HTMLAudioElement | null = null;

export function speakWord(text: string, audioUrl?: string): void {
  // If we have an official MP3 audio URL from the dictionary API, play it first!
  if (audioUrl) {
    try {
      if (activeAudio) {
        activeAudio.pause();
        activeAudio.currentTime = 0;
      }
      activeAudio = new Audio(audioUrl);
      activeAudio.play().catch(() => {
        // If audio playback fails (e.g. CORS or broken link), fallback to speech synthesis
        fallbackSpeechSynthesis(text);
      });
      return;
    } catch {
      // Fallback
    }
  }

  fallbackSpeechSynthesis(text);
}

function fallbackSpeechSynthesis(text: string): void {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    console.warn('Speech synthesis not supported in this browser.');
    return;
  }

  try {
    window.speechSynthesis.cancel(); // cancel any previous utterance
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.rate = 0.9; // Slightly slower for language learners

    // Find English voice if available
    const voices = window.speechSynthesis.getVoices();
    const enVoice = voices.find((v) => v.lang.startsWith('en-') && (v.name.includes('Natural') || v.name.includes('Google') || v.name.includes('Samantha')));
    if (enVoice) {
      utterance.voice = enVoice;
    }

    window.speechSynthesis.speak(utterance);
  } catch (err) {
    console.warn('SpeechSynthesis error:', err);
  }
}
