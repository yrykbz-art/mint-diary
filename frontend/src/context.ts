import { inject, type InjectionKey, type Ref } from 'vue'
import type { ImageAsset } from './api'

export interface ConfirmOptions { title: string; text: string; confirmLabel?: string; cancelLabel?: string; danger?: boolean }
export interface AppContext {
  today: Ref<string>
  notify: (message: string, type?: 'success' | 'error') => void
  confirm: (options: ConfirmOptions) => Promise<boolean>
  preview: (images: ImageAsset[], index: number) => void
}
export const appKey: InjectionKey<AppContext> = Symbol('mint-diary')
export function useApp() { const app = inject(appKey); if (!app) throw new Error('App context missing'); return app }
