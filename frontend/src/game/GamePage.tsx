import { useParams } from 'react-router-dom';

export function GamePage() {
  const { gameId } = useParams();
  return (
    <main className="page">
      <section className="game-placeholder">
        <p className="eyebrow">Match ready</p>
        <h1>The battlefield awaits.</h1>
        <p>Game session <code>{gameId}</code> was created successfully. The authoritative simulation will mount here.</p>
        <div className="canvas-placeholder" aria-label="Future game canvas">Canvas game runtime</div>
      </section>
    </main>
  );
}
