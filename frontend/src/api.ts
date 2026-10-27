export interface ImageAsset { id: string; url: string; originalName: string; size: number }
export interface Diary { date: string; content: string; images: ImageAsset[]; exists: boolean; createdAt: string | null; updatedAt: string | null }
export interface Anniversary { id: number; title: string; date: string; description: string; coverImage: ImageAsset | null; daysPassed: number; future: boolean; daysUntilStart: number }
export interface AnniversaryInput { title: string; date: string; description: string; coverImageId: string | null }
export interface CalendarDay { date: string; hasDiary: boolean; imageCount: number; preview: string; anniversaries: { id: number; title: string; originalDate: string }[] }
export interface CalendarData { month: string; days: CalendarDay[] }

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api${url}`, {
      ...options,
      headers: options.body instanceof FormData ? options.headers : { 'Content-Type': 'application/json', ...options.headers },
    })
  } catch { throw new Error('暂时无法连接到日记服务，请检查 Docker 是否正在运行。') }
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message || (response.status === 413 ? '图片太大了，单张不能超过 10 MB。' : '操作没有完成，请稍后重试。'))
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const api = {
  health: () => request<{ today: string; timeZone: string; status: string }>('/health'),
  diary: (date: string) => request<Diary>(`/diaries/${date}`),
  saveDiary: (date: string, content: string, imageIds: string[]) => request<Diary>(`/diaries/${date}`, { method: 'PUT', body: JSON.stringify({ content, imageIds }) }),
  deleteDiary: (date: string) => request<void>(`/diaries/${date}`, { method: 'DELETE' }),
  calendar: (month: string) => request<CalendarData>(`/calendar?month=${month}`),
  anniversaries: () => request<Anniversary[]>('/anniversaries'),
  saveAnniversary: (data: AnniversaryInput, id?: number) => request<Anniversary>(`/anniversaries${id === undefined ? '' : `/${id}`}`, { method: id === undefined ? 'POST' : 'PUT', body: JSON.stringify(data) }),
  deleteAnniversary: (id: number) => request<void>(`/anniversaries/${id}`, { method: 'DELETE' }),
  upload: (file: File) => { const body = new FormData(); body.append('file', file); return request<ImageAsset>('/images', { method: 'POST', body }) },
  discardImage: (id: string) => request<void>(`/images/${id}`, { method: 'DELETE' }),
}

export const errorMessage = (error: unknown) => error instanceof Error ? error.message : '操作没有完成，请稍后重试。'

export function checkImage(file: File): string | null {
  if (!['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)) return `${file.name}：请选择 JPG、PNG、GIF 或 WebP 图片。`
  if (file.size > 10 * 1024 * 1024) return `${file.name}：单张图片不能超过 10 MB。`
  return null
}
