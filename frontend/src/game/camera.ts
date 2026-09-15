export type Camera = {
  x: number;
  y: number;
  zoom: number;
};

export type Viewport = {
  width: number;
  height: number;
};

type SandboxNavigationOptions = {
  canvas: HTMLCanvasElement;
  getViewport: () => Viewport;
  initialCamera: Camera;
  onZoomChange?: (zoom: number) => void;
};

export const MIN_ZOOM = 0.45;
export const MAX_ZOOM = 2.5;

const MOVEMENT_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']);
const SCREEN_PIXELS_PER_SECOND = 430;

export class SandboxNavigation {
  private readonly canvas: HTMLCanvasElement;
  private readonly getViewport: () => Viewport;
  private readonly initialCamera: Camera;
  private readonly onZoomChange?: (zoom: number) => void;
  private readonly pressedKeys = new Set<string>();
  private cameraState: Camera;
  private attached = false;

  constructor(options: SandboxNavigationOptions) {
    this.canvas = options.canvas;
    this.getViewport = options.getViewport;
    this.initialCamera = { ...options.initialCamera };
    this.cameraState = { ...options.initialCamera };
    this.onZoomChange = options.onZoomChange;
  }

  get camera(): Readonly<Camera> {
    return this.cameraState;
  }

  attach() {
    if (this.attached) return;
    this.attached = true;
    window.addEventListener('keydown', this.handleKeyDown);
    window.addEventListener('keyup', this.handleKeyUp);
    window.addEventListener('blur', this.handleBlur);
    this.canvas.addEventListener('wheel', this.handleWheel, { passive: false });
  }

  detach() {
    if (!this.attached) return;
    this.attached = false;
    this.pressedKeys.clear();
    window.removeEventListener('keydown', this.handleKeyDown);
    window.removeEventListener('keyup', this.handleKeyUp);
    window.removeEventListener('blur', this.handleBlur);
    this.canvas.removeEventListener('wheel', this.handleWheel);
  }

  update(elapsedSeconds: number) {
    const horizontal = Number(this.pressedKeys.has('ArrowRight')) - Number(this.pressedKeys.has('ArrowLeft'));
    const vertical = Number(this.pressedKeys.has('ArrowDown')) - Number(this.pressedKeys.has('ArrowUp'));
    if (horizontal || vertical) {
      this.cameraState = panCamera(this.cameraState, horizontal, vertical, elapsedSeconds);
    }
    return this.camera;
  }

  reset() {
    this.cameraState = { ...this.initialCamera };
    this.pressedKeys.clear();
    this.onZoomChange?.(this.cameraState.zoom);
  }

  private readonly handleKeyDown = (event: KeyboardEvent) => {
    if (!MOVEMENT_KEYS.has(event.key)) return;
    event.preventDefault();
    this.pressedKeys.add(event.key);
  };

  private readonly handleKeyUp = (event: KeyboardEvent) => {
    this.pressedKeys.delete(event.key);
  };

  private readonly handleBlur = () => {
    this.pressedKeys.clear();
  };

  private readonly handleWheel = (event: WheelEvent) => {
    event.preventDefault();
    const bounds = this.canvas.getBoundingClientRect();
    const zoomFactor = Math.exp(-event.deltaY * 0.0015);
    this.cameraState = zoomAtPoint(
      this.cameraState,
      zoomFactor,
      { x: event.clientX - bounds.left, y: event.clientY - bounds.top },
      this.getViewport(),
    );
    this.onZoomChange?.(this.cameraState.zoom);
  };
}

export function clampZoom(zoom: number) {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, zoom));
}

export function zoomAtPoint(
  camera: Camera,
  zoomFactor: number,
  pointer: { x: number; y: number },
  viewport: Viewport,
): Camera {
  const nextZoom = clampZoom(camera.zoom * zoomFactor);
  const worldX = camera.x + (pointer.x - viewport.width / 2) / camera.zoom;
  const worldY = camera.y + (pointer.y - viewport.height / 2) / camera.zoom;

  return {
    x: worldX - (pointer.x - viewport.width / 2) / nextZoom,
    y: worldY - (pointer.y - viewport.height / 2) / nextZoom,
    zoom: nextZoom,
  };
}

export function panCamera(camera: Camera, xDirection: number, yDirection: number, elapsedSeconds: number): Camera {
  const directionLength = Math.hypot(xDirection, yDirection) || 1;
  const distance = (SCREEN_PIXELS_PER_SECOND * elapsedSeconds) / camera.zoom;

  return {
    ...camera,
    x: camera.x + (xDirection / directionLength) * distance,
    y: camera.y + (yDirection / directionLength) * distance,
  };
}
