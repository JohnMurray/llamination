/**
 * Camera position is the center of the viewport in projected world space.
 * Projected world space is the 2D plane produced after the isometric projection;
 * zoom is CSS pixels per projected-world unit.
 */
export type Camera = {
  x: number;
  y: number;
  zoom: number;
};

/** Canvas dimensions in CSS pixels, not backing-store/device pixels. */
export type Viewport = {
  width: number;
  height: number;
};

type SandboxNavigationOptions = {
  canvas: HTMLCanvasElement;
  // Resolve lazily because ResizeObserver can change the viewport after setup.
  getViewport: () => Viewport;
  initialCamera: Camera;
  onZoomChange?: (zoom: number) => void;
};

export const MIN_ZOOM = 0.45;
export const MAX_ZOOM = 2.5;

const MOVEMENT_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight']);
const SCREEN_PIXELS_PER_SECOND = 430;

/**
 * Owns camera input and state while leaving animation timing to the renderer.
 * Call attach/detach with the canvas lifecycle and update once per frame.
 */
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
    // Idempotency matters in development, where React Strict Mode remounts effects.
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
    // Opposing keys cancel naturally; holding a key produces continuous movement.
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
    // A keyup event may never arrive if the browser loses focus mid-pan.
    this.pressedKeys.clear();
  };

  private readonly handleWheel = (event: WheelEvent) => {
    // The listener is non-passive so zooming the game cannot also scroll the page.
    event.preventDefault();
    const bounds = this.canvas.getBoundingClientRect();
    // Exponential scaling makes equal wheel deltas reciprocal in either direction
    // and works for both discrete mouse wheels and high-resolution trackpads.
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
  // Find the world point beneath the pointer before changing zoom. Repositioning
  // the camera around that point keeps it stationary on screen after the change.
  const worldX = camera.x + (pointer.x - viewport.width / 2) / camera.zoom;
  const worldY = camera.y + (pointer.y - viewport.height / 2) / camera.zoom;

  return {
    x: worldX - (pointer.x - viewport.width / 2) / nextZoom,
    y: worldY - (pointer.y - viewport.height / 2) / nextZoom,
    zoom: nextZoom,
  };
}

export function panCamera(camera: Camera, xDirection: number, yDirection: number, elapsedSeconds: number): Camera {
  // Normalize diagonals so two held keys are not faster than one. Dividing by
  // zoom makes the perceived screen-space pan speed independent of zoom level.
  const directionLength = Math.hypot(xDirection, yDirection) || 1;
  const distance = (SCREEN_PIXELS_PER_SECOND * elapsedSeconds) / camera.zoom;

  return {
    ...camera,
    x: camera.x + (xDirection / directionLength) * distance,
    y: camera.y + (yDirection / directionLength) * distance,
  };
}
