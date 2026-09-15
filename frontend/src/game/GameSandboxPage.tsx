import { useEffect, useRef, useState } from 'react';

import { type Camera, SandboxNavigation } from './camera';
import './GameSandboxPage.css';

const INITIAL_CAMERA: Camera = { x: 0, y: 10, zoom: 1 };

export function GameSandboxPage() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const navigationRef = useRef<SandboxNavigation>(null);
  const [zoomPercent, setZoomPercent] = useState(100);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const context = canvas.getContext('2d');
    if (!context) return;

    let viewport = { width: 1, height: 1 };
    let animationFrame = 0;
    let previousTime = performance.now();

    const resizeCanvas = () => {
      const bounds = canvas.getBoundingClientRect();
      const pixelRatio = window.devicePixelRatio || 1;
      viewport = { width: bounds.width, height: bounds.height };
      canvas.width = Math.round(bounds.width * pixelRatio);
      canvas.height = Math.round(bounds.height * pixelRatio);
    };

    const resizeObserver = new ResizeObserver(resizeCanvas);
    resizeObserver.observe(canvas);
    resizeCanvas();
    const navigation = new SandboxNavigation({
      canvas,
      getViewport: () => viewport,
      initialCamera: INITIAL_CAMERA,
      onZoomChange: (zoom) => setZoomPercent(Math.round(zoom * 100)),
    });
    navigation.attach();
    navigationRef.current = navigation;

    const render = (time: number) => {
      const elapsedSeconds = Math.min((time - previousTime) / 1000, 0.05);
      previousTime = time;

      drawScene(context, viewport, navigation.update(elapsedSeconds));
      animationFrame = requestAnimationFrame(render);
    };

    animationFrame = requestAnimationFrame(render);
    return () => {
      cancelAnimationFrame(animationFrame);
      resizeObserver.disconnect();
      navigation.detach();
      if (navigationRef.current === navigation) navigationRef.current = null;
    };
  }, []);

  const resetCamera = () => {
    navigationRef.current?.reset();
  };

  return (
    <main className="game-sandbox">
      <canvas ref={canvasRef} className="game-canvas" aria-label="Isometric game sandbox with a small colorful building" />
      <header className="sandbox-title">
        <p className="eyebrow">Renderer sandbox</p>
        <h1>Sunny pasture</h1>
      </header>
      <aside className="camera-help" aria-label="Camera controls">
        <div className="zoom-readout"><span>Camera</span><strong>{zoomPercent}%</strong></div>
        <div className="control-hint">
          <span className="mouse-icon" aria-hidden="true" />
          <p><strong>Scroll</strong><span>Zoom in / out</span></p>
        </div>
        <div className="control-hint">
          <span className="key-cluster" aria-hidden="true">↑<br />← ↓ →</span>
          <p><strong>Arrow keys</strong><span>Move camera</span></p>
        </div>
        <button type="button" className="reset-camera" onClick={resetCamera}>Reset view</button>
      </aside>
    </main>
  );
}

function drawScene(context: CanvasRenderingContext2D, viewport: { width: number; height: number }, camera: Camera) {
  const pixelRatio = window.devicePixelRatio || 1;
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
  const sky = context.createLinearGradient(0, 0, 0, viewport.height);
  sky.addColorStop(0, '#bde8dd');
  sky.addColorStop(1, '#f5e9b8');
  context.fillStyle = sky;
  context.fillRect(0, 0, viewport.width, viewport.height);

  context.save();
  context.translate(viewport.width / 2, viewport.height / 2);
  context.scale(camera.zoom, camera.zoom);
  context.translate(-camera.x, -camera.y);
  drawTerrain(context);
  drawDecorations(context);
  drawBuilding(context);
  context.restore();

  context.setTransform(1, 0, 0, 1, 0, 0);
}

function drawTerrain(context: CanvasRenderingContext2D) {
  const tileWidth = 128;
  const tileHeight = 64;
  const mapSize = 1_280;
  const tileRadius = 16;

  context.save();
  context.beginPath();
  context.rect(-mapSize / 2, -mapSize / 2, mapSize, mapSize);
  context.clip();
  context.fillStyle = '#79b866';
  context.fillRect(-mapSize / 2, -mapSize / 2, mapSize, mapSize);

  for (let row = -tileRadius; row <= tileRadius; row += 1) {
    for (let column = -tileRadius; column <= tileRadius; column += 1) {
      const x = (column - row) * tileWidth / 2;
      const y = (column + row) * tileHeight / 2;
      context.beginPath();
      context.moveTo(x, y - tileHeight / 2);
      context.lineTo(x + tileWidth / 2, y);
      context.lineTo(x, y + tileHeight / 2);
      context.lineTo(x - tileWidth / 2, y);
      context.closePath();
      context.fillStyle = (row + column) % 2 === 0 ? '#79b866' : '#72ae60';
      context.fill();
      context.strokeStyle = '#5a944f55';
      context.lineWidth = 1;
      context.stroke();
    }
  }

  context.restore();
  context.strokeStyle = '#3f7947';
  context.lineWidth = 8;
  context.strokeRect(-mapSize / 2, -mapSize / 2, mapSize, mapSize);
}

function drawDecorations(context: CanvasRenderingContext2D) {
  const flowers = [
    [-315, -70, '#ffe47a'], [-260, 102, '#f28cad'], [285, -118, '#fff0a0'],
    [350, 84, '#ef8fa4'], [-440, 50, '#f5c35d'], [470, -8, '#f29ac2'],
  ] as const;
  for (const [x, y, color] of flowers) {
    context.fillStyle = '#377f47';
    context.fillRect(x - 1, y, 3, 12);
    context.fillStyle = color;
    context.fillRect(x - 5, y - 5, 11, 8);
  }
}

function drawBuilding(context: CanvasRenderingContext2D) {
  context.fillStyle = '#31543c55';
  context.fillRect(-119, 72, 258, 42);

  context.fillStyle = '#e59a57';
  context.fillRect(-100, -62, 200, 148);

  context.fillStyle = '#f0b56d';
  context.fillRect(-100, -62, 28, 148);

  context.fillStyle = '#c95750';
  context.fillRect(-122, -82, 244, 28);
  context.fillStyle = '#db6b5b';
  context.fillRect(-105, -106, 210, 26);
  context.fillStyle = '#ef876a';
  context.fillRect(-78, -126, 156, 22);

  context.fillStyle = '#6b4937';
  context.fillRect(-25, 20, 50, 66);
  context.fillStyle = '#855c42';
  context.fillRect(-19, 28, 38, 58);
  context.fillStyle = '#f5cc63';
  context.fillRect(10, 55, 7, 7);

  context.fillStyle = '#ecf4d8';
  context.fillRect(-76, -26, 38, 40);
  context.fillRect(38, -26, 38, 40);
  context.fillStyle = '#65b9c0';
  context.fillRect(-70, -20, 26, 28);
  context.fillRect(44, -20, 26, 28);
  context.fillStyle = '#477d88';
  context.fillRect(-59, -20, 4, 28);
  context.fillRect(55, -20, 4, 28);
  context.fillRect(-70, -8, 26, 4);
  context.fillRect(44, -8, 26, 4);

  context.fillStyle = '#fbce68';
  context.fillRect(-68, 30, 24, 8);
  context.fillStyle = '#4f9a58';
  context.fillRect(-65, 21, 6, 10);
  context.fillRect(-54, 17, 6, 14);
}
