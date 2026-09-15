import {
  calculateMinimapViewRect,
  createCenteredMapBounds,
  MinimapInteraction,
  minimapPointToWorld,
} from './minimap';

const mapBounds = createCenteredMapBounds(1_280);
const minimapViewport = { width: 200, height: 200 };

describe('calculateMinimapViewRect', () => {
  it('maps a zoomed camera view into minimap coordinates', () => {
    const result = calculateMinimapViewRect(
      { x: 0, y: 0, zoom: 2 },
      { width: 800, height: 600 },
      mapBounds,
      minimapViewport,
    );

    expect(result).toEqual({ x: 68.75, y: 76.5625, width: 62.5, height: 46.875 });
  });

  it('clips the camera view to the map boundary', () => {
    const result = calculateMinimapViewRect(
      { x: -600, y: 0, zoom: 1 },
      { width: 400, height: 400 },
      mapBounds,
      minimapViewport,
    );

    expect(result).toEqual({ x: 0, y: 68.75, width: 37.5, height: 62.5 });
  });

  it('covers the entire minimap when the whole map is visible', () => {
    const result = calculateMinimapViewRect(
      { x: 0, y: 0, zoom: .25 },
      { width: 800, height: 800 },
      mapBounds,
      minimapViewport,
    );

    expect(result).toEqual({ x: 0, y: 0, width: 200, height: 200 });
  });

  it('returns no rectangle when the camera does not intersect the map', () => {
    const result = calculateMinimapViewRect(
      { x: 2_000, y: 2_000, zoom: 2 },
      { width: 400, height: 400 },
      mapBounds,
      minimapViewport,
    );

    expect(result).toBeNull();
  });
});

describe('minimapPointToWorld', () => {
  it('maps minimap coordinates into world coordinates', () => {
    expect(minimapPointToWorld({ x: 50, y: 150 }, minimapViewport, mapBounds)).toEqual({ x: -320, y: 320 });
  });

  it('clamps positions outside the minimap to the map boundary', () => {
    expect(minimapPointToWorld({ x: -20, y: 240 }, minimapViewport, mapBounds)).toEqual({ x: -640, y: 640 });
  });
});

describe('MinimapInteraction', () => {
  let canvas: HTMLCanvasElement;
  let interaction: MinimapInteraction | undefined;

  beforeEach(() => {
    canvas = document.createElement('canvas');
    vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({
      x: 100,
      y: 50,
      top: 50,
      right: 300,
      bottom: 250,
      left: 100,
      width: 200,
      height: 200,
      toJSON: () => ({}),
    });
  });

  afterEach(() => interaction?.detach());

  it('navigates on click and continuously while dragging', () => {
    const onNavigate = vi.fn();
    interaction = new MinimapInteraction(canvas, mapBounds, onNavigate);
    interaction.attach();

    const mouseDown = new MouseEvent('mousedown', { button: 0, clientX: 200, clientY: 150, cancelable: true });
    expect(canvas.dispatchEvent(mouseDown)).toBe(false);
    expect(onNavigate).toHaveBeenLastCalledWith({ x: 0, y: 0 });

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 300, clientY: 250 }));
    expect(onNavigate).toHaveBeenLastCalledWith({ x: 640, y: 640 });

    window.dispatchEvent(new MouseEvent('mouseup', { button: 0, clientX: 250, clientY: 100 }));
    expect(onNavigate).toHaveBeenLastCalledWith({ x: 320, y: -320 });
  });

  it('stops navigating after mouseup and removes listeners when detached', () => {
    const onNavigate = vi.fn();
    interaction = new MinimapInteraction(canvas, mapBounds, onNavigate);
    interaction.attach();
    canvas.dispatchEvent(new MouseEvent('mousedown', { button: 0, clientX: 200, clientY: 150 }));
    window.dispatchEvent(new MouseEvent('mouseup', { button: 0, clientX: 200, clientY: 150 }));
    onNavigate.mockClear();

    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 250, clientY: 200 }));
    expect(onNavigate).not.toHaveBeenCalled();

    interaction.detach();
    canvas.dispatchEvent(new MouseEvent('mousedown', { button: 0, clientX: 200, clientY: 150 }));
    expect(onNavigate).not.toHaveBeenCalled();
  });
});
