import { useEffect, useRef } from "react";
import { createScope, createTimer } from "animejs";
import { POINT_COLORS, SHAPES } from "../landing/shapes";

export type ManagerPhase =
  | "idle"
  | "loading"
  | "typing"
  | "thinking"
  | "ready"
  | "error";

interface AiManagerCoreProps {
  phase: ManagerPhase;
  compact: boolean;
  paused: boolean;
}

/** 3D 좌표를 Canvas 2D에 투영한다. WebGL이나 추가 네트워크 요청은 필요하지 않다. */
export default function AiManagerCore({
  phase,
  compact,
  paused,
}: AiManagerCoreProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const phaseRef = useRef(phase);
  const pausedRef = useRef(paused);
  const updateMotion = useRef<() => void>(() => {});

  useEffect(() => {
    phaseRef.current = phase;
    updateMotion.current();
  }, [phase]);
  useEffect(() => {
    pausedRef.current = paused;
    updateMotion.current();
  }, [paused]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) return;
    const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const coarse = window.matchMedia("(pointer: coarse)");
    let inView = true;
    let width = 0;
    let height = 0;
    let rotation = 0.45;
    let pointerX = 0;
    let pointerY = 0;
    const points = SHAPES[0].map((point) => ({ ...point }));
    const shapeForPhase = () =>
      phaseRef.current === "thinking"
        ? 2
        : phaseRef.current === "ready"
          ? 4
          : 0;

    const draw = (moving = false) => {
      if (!width || !height) return;
      const target = SHAPES[shapeForPhase()];
      if (moving) rotation += phaseRef.current === "thinking" ? 0.009 : 0.0035;
      const cy = Math.cos(rotation + pointerX);
      const sy = Math.sin(rotation + pointerX);
      const cx = Math.cos(0.42 + pointerY);
      const sx = Math.sin(0.42 + pointerY);
      const scale = Math.min(width, height) * 0.32;
      context.clearRect(0, 0, width, height);
      const projected = points.map((point, index) => {
        const blend = moving ? 0.065 : 1;
        point.x += (target[index].x - point.x) * blend;
        point.y += (target[index].y - point.y) * blend;
        point.z += (target[index].z - point.z) * blend;
        const x = point.x * cy - point.z * sy;
        const z = point.x * sy + point.z * cy;
        const y = point.y * cx - z * sx;
        const depth = point.y * sx + z * cx;
        const perspective = 3.5 / (3.5 + depth);
        return {
          x: width / 2 + x * scale * perspective,
          y: height / 2 + y * scale * perspective,
          depth,
          perspective,
          index,
        };
      });
      context.lineWidth = 0.65;
      // 점당 세 연결만 검사해 매 프레임의 연산량을 제한한다.
      for (let index = 0; index < projected.length; index += 1) {
        const a = projected[index];
        for (const offset of [1, 6, 13]) {
          const b = projected[(index + offset) % projected.length];
          const distance = Math.hypot(a.x - b.x, a.y - b.y);
          if (distance > scale * 0.44 || Math.abs(a.depth - b.depth) > 0.6)
            continue;
          context.strokeStyle = `rgba(150,140,245,${0.14 * (1 - distance / (scale * 0.44))})`;
          context.beginPath();
          context.moveTo(a.x, a.y);
          context.lineTo(b.x, b.y);
          context.stroke();
        }
      }
      projected
        .sort((a, b) => b.depth - a.depth)
        .forEach((point) => {
          const color =
            phaseRef.current === "error"
              ? [248, 163, 179]
              : POINT_COLORS[point.index];
          const alpha = Math.min(1, Math.max(0.3, 0.68 - point.depth * 0.26));
          context.fillStyle = `rgba(${color.join(",")},${alpha})`;
          context.beginPath();
          context.arc(
            point.x,
            point.y,
            Math.max(1, 2.1 * point.perspective),
            0,
            Math.PI * 2,
          );
          context.fill();
        });
    };

    const scope = createScope({ root: canvas.parentElement! });
    const timer = scope.execute(() =>
      createTimer({
        autoplay: false,
        frameRate: 30,
        onUpdate: () => draw(true),
      }),
    );
    const syncMotion = () => {
      const stopped =
        motion.matches || pausedRef.current || !inView || document.hidden;
      if (stopped) {
        timer.pause();
        if (inView && !document.hidden) draw();
      } else {
        timer.play();
      }
    };
    updateMotion.current = syncMotion;
    const resize = () => {
      const bounds = canvas.getBoundingClientRect();
      width = bounds.width;
      height = bounds.height;
      const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
      draw();
    };
    const pointerMove = (event: PointerEvent) => {
      if (motion.matches || pausedRef.current || coarse.matches) return;
      const bounds = canvas.getBoundingClientRect();
      pointerX =
        ((event.clientX - bounds.left) / Math.max(1, bounds.width) - 0.5) *
        0.25;
      pointerY =
        ((event.clientY - bounds.top) / Math.max(1, bounds.height) - 0.5) *
        0.15;
    };
    const pointerLeave = () => {
      pointerX = 0;
      pointerY = 0;
    };
    const resizeObserver = new ResizeObserver(resize);
    const intersectionObserver = new IntersectionObserver(([entry]) => {
      inView = entry.isIntersecting;
      syncMotion();
    });
    resizeObserver.observe(canvas);
    intersectionObserver.observe(canvas);
    canvas.addEventListener("pointermove", pointerMove);
    canvas.addEventListener("pointerleave", pointerLeave);
    document.addEventListener("visibilitychange", syncMotion);
    motion.addEventListener("change", syncMotion);
    resize();
    syncMotion();
    return () => {
      updateMotion.current = () => {};
      resizeObserver.disconnect();
      intersectionObserver.disconnect();
      canvas.removeEventListener("pointermove", pointerMove);
      canvas.removeEventListener("pointerleave", pointerLeave);
      document.removeEventListener("visibilitychange", syncMotion);
      motion.removeEventListener("change", syncMotion);
      scope.revert();
    };
  }, []);

  return (
    <div
      className={`am-core ${compact ? "am-core--compact" : ""}`}
      data-phase={phase}
      aria-hidden="true"
    >
      <div className="am-core-glow" />
      <div className="am-core-orbit" />
      <canvas ref={canvasRef} className="am-core-canvas" />
    </div>
  );
}
