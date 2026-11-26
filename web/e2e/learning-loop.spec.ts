import { expect, test, type Page } from '@playwright/test'

const lesson = {
  id: 1,
  title: 'A small beginning',
  description: 'A short lesson for testing.',
  collection: { id: 7, title: 'Starter stories' },
  image: null,
  audio: null,
  level: 1,
  duration: 90,
}

async function mockLingq(page: Page) {
  const methods: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/lingq/')) methods.push(request.method())
  })
  await page.route('**/api/lingq/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/lingq', '')
    const json = (body: unknown, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })
    if (path === '/api/v2/api-token-auth/' && request.method() === 'POST') return json({ token: 'test-token' })
    if (path === '/api/v3/profiles/') return json({ username: 'reader', display_name: 'Reader', language: { code: 'en' } })
    if (path === '/api/v2/languages/') return json({ results: [{ code: 'en', title: 'English', is_active: true }] })
    if (path === '/api/v3/en/shelves/') return json({ sections: [{ title: 'Continue learning', lessons: [lesson] }] })
    if (path === '/api/v3/en/providers/') return json({ results: [{ id: 'lingq', title: 'LingQ' }] })
    if (path === '/api/v3/en/search/') return json({ results: [lesson] })
    if (path === '/api/v3/en/lessons/1/text/') return json({ text: 'Hello world.\nReading is fun.' })
    if (path === '/api/v3/en/lessons/1/sentences/') return json({ results: [
      { index: 9, text: 'Hello world.' },
      { index: 3, text: 'Reading is fun.' },
    ] })
    if (path === '/api/v3/en/lessons/1/') return json(lesson)
    if (path === '/api/v2/en/hints/search/') {
      const term = url.searchParams.get('term')?.toLowerCase()
      return json({ results: [{ text: term === 'hello' ? 'a greeting' : 'the process of understanding text', locale: 'en' }] })
    }
    return json({ detail: 'Not found' }, 404)
  })
  return methods
}

async function signIn(page: Page) {
  await page.goto('/login')
  await page.getByLabel('Username').fill('reader')
  await page.getByLabel('Password').fill('not-a-real-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Continue learning', exact: true })).toBeVisible()
}

test('completes the local reading, vocabulary and review loop without write APIs', async ({ page }) => {
  const methods = await mockLingq(page)
  await signIn(page)
  await page.getByRole('link', { name: 'Open lesson' }).click()
  await expect(page.getByRole('heading', { name: lesson.title })).toBeVisible()
  await page.getByRole('link', { name: 'Start reading' }).click()
  await expect(page.getByRole('button', { name: /^hello$/i })).toBeVisible()

  await page.getByRole('button', { name: /^hello$/i }).click()
  const helloDialog = page.getByRole('dialog', { name: /^hello$/i })
  await expect(helloDialog.getByText('a greeting')).toBeVisible()
  await helloDialog.getByRole('button', { name: 'Known', exact: true }).click()
  await expect(helloDialog).toBeHidden()

  await page.getByRole('button', { name: 'world', exact: true }).click()
  const worldDialog = page.getByRole('dialog', { name: 'world' })
  await expect(worldDialog.getByText('the process of understanding text')).toBeVisible()
  await worldDialog.getByRole('button', { name: 'Close', exact: true }).click()

  await page.getByRole('link', { name: 'Back', exact: true }).click()
  await page.getByRole('link', { name: 'Vocabulary', exact: true }).click()
  await expect(page.getByRole('button', { name: /hello/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /world/ })).toBeVisible()
  await page.getByRole('link', { name: 'Review', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'world', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Show meaning' }).click()
  await expect(page.getByText('the process of understanding text')).toBeVisible()
  await page.getByRole('button', { name: 'I remembered' }).click()
  await expect(page.getByRole('heading', { name: 'Review complete' })).toBeVisible()

  const storage = await page.evaluate(() => ({
    local: { ...localStorage },
    session: { ...sessionStorage },
  }))
  expect(JSON.stringify(storage)).not.toContain('test-token')
  expect(methods.filter((method) => method !== 'GET')).toEqual(['POST'])
})

test('keeps the login and navigation usable on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockLingq(page)
  await signIn(page)
  await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Explore', exact: true })).toBeVisible()
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  expect(overflow).toBe(false)
  await page.getByRole('link', { name: 'Settings', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Settings', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Explore', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Explore', exact: true })).toBeVisible()
})

test('applies a provider filter without a text query', async ({ page }) => {
  const searches: URL[] = []
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (url.pathname === '/api/lingq/api/v3/en/search/') searches.push(url)
  })
  await mockLingq(page)
  await signIn(page)
  await page.getByRole('link', { name: 'Explore', exact: true }).click()
  await page.getByRole('button', { name: 'LingQ', exact: true }).click()
  await expect.poll(() => searches.length).toBe(1)
  expect(searches[0].searchParams.get('q')).toBeNull()
  expect(searches[0].searchParams.get('provider')).toBe('lingq')
})
