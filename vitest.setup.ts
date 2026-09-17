/**
 * Test environment setup.
 *
 * The suite runs in the plain node environment (no jsdom), which has no
 * localStorage. This minimal in-memory stub keeps the persistence paths of the
 * zustand stores exercised instead of throwing "localStorage is not defined".
 */
const memoryStorage = new Map<string, string>();

const storageStub: Storage = {
  getItem: (key) => memoryStorage.get(key) ?? null,
  setItem: (key, value) => {
    memoryStorage.set(key, String(value));
  },
  removeItem: (key) => {
    memoryStorage.delete(key);
  },
  clear: () => {
    memoryStorage.clear();
  },
  key: (index) => Array.from(memoryStorage.keys())[index] ?? null,
  get length() {
    return memoryStorage.size;
  },
};

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: storageStub,
});
