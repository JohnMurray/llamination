import { calculateMinimapViewRect, createCenteredMapBounds } from './minimap';

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
