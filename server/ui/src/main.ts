import { mount } from 'svelte'
import './app.css'
import App from './App.svelte'
import 'vidstack/define/media-player'
import 'vidstack/define/media-outlet'
import 'vidstack/define/media-poster'
import 'vidstack/define/media-community-skin'

const app = mount(App, {
  target: document.getElementById('app')!,
})

export default app
