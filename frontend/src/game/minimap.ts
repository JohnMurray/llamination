import type { Camera, Viewport } from './camera';

export type WorldBounds = {
  left: number;
  top: number;
  right: number;
  bottom: number;
};

export type Rectangle = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export function createCenteredMapBounds(size: number): WorldBounds {
  return { left: -size / 2, top: -size / 2, right: size / 2, bottom: size / 2 };
}

export function calculateMinimapViewRect(
  camera: Readonly<Camera>,
  gameViewport: Viewport,
  mapBounds: WorldBounds,
  minimapViewport: Viewport,
): Rectangle | null {
  const halfViewWidth = gameViewport.width / camera.zoom / 2;
  const halfViewHeight = gameViewport.height / camera.zoom / 2;
  const visibleWorld = {
    left: Math.max(mapBounds.left, camera.x - halfViewWidth),
    top: Math.max(mapBounds.top, camera.y - halfViewHeight),
    right: Math.min(mapBounds.right, camera.x + halfViewWidth),
    bottom: Math.min(mapBounds.bottom, camera.y + halfViewHeight),
  };

  if (visibleWorld.left >= visibleWorld.right || visibleWorld.top >= visibleWorld.bottom) return null;

  const scaleX = minimapViewport.width / (mapBounds.right - mapBounds.left);
  const scaleY = minimapViewport.height / (mapBounds.bottom - mapBounds.top);
  return {
    x: (visibleWorld.left - mapBounds.left) * scaleX,
    y: (visibleWorld.top - mapBounds.top) * scaleY,
    width: (visibleWorld.right - visibleWorld.left) * scaleX,
    height: (visibleWorld.bottom - visibleWorld.top) * scaleY,
  };
}

export class MinimapRenderer {
  private readonly context: CanvasRenderingContext2D;
  private readonly baseCanvas = document.createElement('canvas');
  private readonly baseContext: CanvasRenderingContext2D;
  private viewport: Viewport = { width: 0, height: 0 };
  private pixelRatio = 1;

  constructor(private readonly canvas: HTMLCanvasElement, private readonly mapBounds: WorldBounds) {
    const context = canvas.getContext('2d');
    const baseContext = this.baseCanvas.getContext('2d');
    if (!context || !baseContext) throw new Error('Canvas 2D is required to render the minimap');
    this.context = context;
    this.baseContext = baseContext;
  }

  resize() {
    const bounds = this.canvas.getBoundingClientRect();
    if (!bounds.width || !bounds.height) return;

    const nextViewport = { width: bounds.width, height: bounds.height };
    const nextPixelRatio = window.devicePixelRatio || 1;
    const pixelWidth = Math.round(bounds.width * nextPixelRatio);
    const pixelHeight = Math.round(bounds.height * nextPixelRatio);
    const cacheIsCurrent = this.baseCanvas.width === pixelWidth
      && this.baseCanvas.height === pixelHeight
      && this.viewport.width === nextViewport.width
      && this.viewport.height === nextViewport.height;

    this.viewport = nextViewport;
    this.pixelRatio = nextPixelRatio;
    if (cacheIsCurrent) return;

    if (this.canvas.width !== pixelWidth) this.canvas.width = pixelWidth;
    if (this.canvas.height !== pixelHeight) this.canvas.height = pixelHeight;
    this.baseCanvas.width = pixelWidth;
    this.baseCanvas.height = pixelHeight;
    this.baseContext.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
    drawLowFidelityMap(this.baseContext, this.viewport);
  }

  render(camera: Readonly<Camera>, gameViewport: Viewport) {
    if (!this.viewport.width || !this.viewport.height) return;

    const { width, height } = this.viewport;
    this.context.setTransform(1, 0, 0, 1, 0, 0);
    this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
    this.context.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
    this.context.imageSmoothingEnabled = false;
    this.drawCachedMap();

    this.context.fillStyle = '#4750558c';
    this.context.fillRect(0, 0, width, height);

    const viewRect = calculateMinimapViewRect(camera, gameViewport, this.mapBounds, this.viewport);
    if (!viewRect) return;

    this.context.save();
    this.context.beginPath();
    this.context.rect(viewRect.x, viewRect.y, viewRect.width, viewRect.height);
    this.context.clip();
    this.drawCachedMap();
    this.context.restore();

    const inset = 1.5;
    this.context.strokeStyle = '#e3423b';
    this.context.lineWidth = 3;
    this.context.strokeRect(
      viewRect.x + inset,
      viewRect.y + inset,
      Math.max(0, viewRect.width - inset * 2),
      Math.max(0, viewRect.height - inset * 2),
    );
  }

  private drawCachedMap() {
    this.context.drawImage(
      this.baseCanvas,
      0,
      0,
      this.baseCanvas.width,
      this.baseCanvas.height,
      0,
      0,
      this.viewport.width,
      this.viewport.height,
    );
  }
}

function drawLowFidelityMap(context: CanvasRenderingContext2D, viewport: Viewport) {
  context.fillStyle = '#73ad61';
  context.fillRect(0, 0, viewport.width, viewport.height);

  context.strokeStyle = '#4f8a4d55';
  context.lineWidth = 1;
  const spacing = Math.max(12, viewport.width / 12);
  for (let offset = -viewport.height; offset < viewport.width; offset += spacing) {
    context.beginPath();
    context.moveTo(offset, 0);
    context.lineTo(offset + viewport.height, viewport.height);
    context.stroke();
    context.beginPath();
    context.moveTo(offset, viewport.height);
    context.lineTo(offset + viewport.height, 0);
    context.stroke();
  }

  const centerX = viewport.width / 2;
  const centerY = viewport.height / 2;
  context.fillStyle = '#c95750';
  context.fillRect(centerX - viewport.width * .055, centerY - viewport.height * .05, viewport.width * .11, viewport.height * .035);
  context.fillStyle = '#e2a05f';
  context.fillRect(centerX - viewport.width * .045, centerY - viewport.height * .015, viewport.width * .09, viewport.height * .08);
  context.fillStyle = '#6b4937';
  context.fillRect(centerX - viewport.width * .009, centerY + viewport.height * .025, viewport.width * .018, viewport.height * .04);

  const landmarks = [
    [.25, .42, '#ffe47a'], [.30, .58, '#f28cad'], [.72, .37, '#fff0a0'],
    [.78, .57, '#ef8fa4'], [.16, .54, '#f5c35d'], [.85, .49, '#f29ac2'],
  ] as const;
  for (const [x, y, color] of landmarks) {
    context.fillStyle = color;
    context.fillRect(viewport.width * x - 2, viewport.height * y - 2, 4, 4);
  }
}
