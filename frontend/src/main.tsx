import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles/app.css';
import './styles/sources/youtube.css';
import './styles/sources/youtube-player.css';
import './styles/sources/youtube-transcript.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
