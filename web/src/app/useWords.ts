import { useCallback, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from './AuthContext'
import { listWords, deleteWord, putWord } from '../storage/db'
import type { WordStatus } from '../api/types'

export function useWords() {
  const { language, token } = useAuth()
  const queryClient = useQueryClient()
  const key = useMemo(() => ['words', language] as const, [language])
  const query = useQuery({
    queryKey: key,
    queryFn: () => listWords(language ?? ''),
    enabled: Boolean(language && token),
    staleTime: 30_000,
  })
  const mutation = useMutation({
    mutationFn: ({ term, status }: { term: string; status: WordStatus }) =>
      putWord(language ?? '', term, status),
    onMutate: async ({ term, status }) => {
      await queryClient.cancelQueries({ queryKey: key })
      const previous = queryClient.getQueryData<Awaited<ReturnType<typeof listWords>>>(key)
      const normalized = term.trim().toLowerCase()
      queryClient.setQueryData(key, (current: Awaited<ReturnType<typeof listWords>> = []) => [
        ...current.filter((word) => word.term !== normalized),
        { language: language ?? '', term: normalized, status, updatedAt: new Date().toISOString() },
      ])
      return { previous }
    },
    onError: (_error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
  })
  const remove = useMutation({
    mutationFn: (term: string) => deleteWord(language ?? '', term),
    onSettled: () => queryClient.invalidateQueries({ queryKey: key }),
  })
  const setStatus = useCallback((term: string, status: WordStatus) => {
    return mutation.mutateAsync({ term, status })
  }, [mutation])
  const removeWord = useCallback((term: string) => remove.mutateAsync(term), [remove])
  const wordMap = useMemo(
    () => new Map((query.data ?? []).map((word) => [word.term, word.status])),
    [query.data],
  )
  return {
    words: query.data ?? [],
    wordMap,
    loading: query.isLoading,
    setStatus,
    removeWord,
    saving: mutation.isPending,
  }
}
