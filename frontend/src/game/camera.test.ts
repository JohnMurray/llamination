import { MAX_ZOOM, MIN_ZOOM, SandboxNavigation, panCamera, zoomAtPoint } from './camera';

describe('camera transforms', () => {
  it('keeps the world point beneath the cursor fixed while zooming', () => {
    const viewport = { width: 800, height: 600 };
    const pointer = { x: 600, y: 200 };
    const before = { x: 40, y: -20, zoom: 1 };
    const worldBefore = {
      x: before.x + (pointer.x - viewport.width / 2) / before.zoom,
      y: before.y + (pointer.y - viewport.height / 2) / before.zoom,
    };

    const after = zoomAtPoint(before, 1.2, pointer, viewport);
    const worldAfter = {
      x: after.x + (pointer.x - viewport.width / 2) / after.zoom,
      y: after.y + (pointer.y - viewport.height / 2) / after.zoom,
    };

    expect(worldAfter.x).toBeCloseTo(worldBefore.x);
    expect(worldAfter.y).toBeCloseTo(worldBefore.y);
  });

  it('clamps zoom to the supported range', () => {
    const viewport = { width: 800, height: 600 };
    expect(zoomAtPoint({ x: 0, y: 0, zoom: 1 }, 100, { x: 400, y: 300 }, viewport).zoom).toBe(MAX_ZOOM);
    expect(zoomAtPoint({ x: 0, y: 0, zoom: 1 }, 0.001, { x: 400, y: 300 }, viewport).zoom).toBe(MIN_ZOOM);
  });

  it('keeps diagonal panning at the same speed as cardinal panning', () => {
    const cardinal = panCamera({ x: 0, y: 0, zoom: 1 }, 1, 0, 1);
    const diagonal = panCamera({ x: 0, y: 0, zoom: 1 }, 1, 1, 1);

    expect(Math.hypot(diagonal.x, diagonal.y)).toBeCloseTo(cardinal.x);
  });
});

describe('SandboxNavigation', () => {
  let canvas: HTMLCanvasElement;
  let navigation: SandboxNavigation | undefined;

  beforeEach(() => {
    canvas = document.createElement('canvas');
    vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({
      x: 100,
      y: 50,
      top: 50,
      right: 900,
      bottom: 650,
      left: 100,
      width: 800,
      height: 600,
      toJSON: () => ({}),
    });
  });

  afterEach(() => navigation?.detach());

  it('pans continuously while an arrow key is held', () => {
    navigation = createNavigation(canvas);
    navigation.attach();

    const keyDown = new KeyboardEvent('keydown', { key: 'ArrowRight', cancelable: true });
    expect(window.dispatchEvent(keyDown)).toBe(false);
    expect(navigation.update(0.1).x).toBeGreaterThan(0);

    window.dispatchEvent(new KeyboardEvent('keyup', { key: 'ArrowRight' }));
    const stoppedX = navigation.camera.x;
    expect(navigation.update(0.1).x).toBe(stoppedX);
  });

  it('zooms on canvas wheel events and reports the new zoom', () => {
    const onZoomChange = vi.fn();
    navigation = createNavigation(canvas, onZoomChange);
    navigation.attach();

    const wheel = new WheelEvent('wheel', { deltaY: -200, clientX: 500, clientY: 350, cancelable: true });
    expect(canvas.dispatchEvent(wheel)).toBe(false);
    expect(navigation.camera.zoom).toBeGreaterThan(1);
    expect(onZoomChange).toHaveBeenCalledWith(navigation.camera.zoom);
  });

  it('resets camera state and removes listeners when detached', () => {
    const onZoomChange = vi.fn();
    navigation = createNavigation(canvas, onZoomChange);
    navigation.attach();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    navigation.update(0.1);

    navigation.reset();
    expect(navigation.camera).toEqual({ x: 0, y: 10, zoom: 1 });
    expect(onZoomChange).toHaveBeenLastCalledWith(1);

    navigation.detach();
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    expect(navigation.update(0.1)).toEqual({ x: 0, y: 10, zoom: 1 });
  });
});

function createNavigation(canvas: HTMLCanvasElement, onZoomChange?: (zoom: number) => void) {
  return new SandboxNavigation({
    canvas,
    getViewport: () => ({ width: 800, height: 600 }),
    initialCamera: { x: 0, y: 10, zoom: 1 },
    onZoomChange,
  });
}
