"use client";

import { useEffect, useRef } from "react";

const FONT_CN =
  '"Noto Sans SC","PingFang SC","Hiragino Sans GB","Microsoft YaHei",system-ui,sans-serif';

type TitleBounds = {
  charCount: number;
  dpr: number;
  maxX: number;
  maxY: number;
  minX: number;
  minY: number;
};

function makeCanvas(width: number, height: number) {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  return canvas;
}

function paintTitle(canvas: HTMLCanvasElement): TitleBounds | null {
  const rect = canvas.getBoundingClientRect();
  if (!rect.width || !rect.height) {
    return null;
  }

  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const width = Math.round(rect.width * dpr);
  const height = Math.round(rect.height * dpr);
  if (width < 1 || height < 1) {
    return null;
  }
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }

  const context = canvas.getContext("2d");
  if (!context) {
    return null;
  }
  context.clearRect(0, 0, width, height);

  const text = "开始创作";
  const lines = [text];
  const probe = makeCanvas(8, 8).getContext("2d");
  if (!probe) {
    return null;
  }

  probe.font = `700 100px ${FONT_CN}`;
  if ("letterSpacing" in probe) {
    (probe as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = "4px";
  }
  let maxWidth = 0;
  lines.forEach((line) => {
    maxWidth = Math.max(maxWidth, probe.measureText(line).width);
  });

  const ratio = maxWidth / 100 || 1;
  const lineHeight = 1;
  const paddingX = Math.max(8, width * 0.018);
  const size = Math.max(
    20,
    Math.min(
      ((width - paddingX * 2) * 0.96) / ratio,
      (height * 0.98) / (lines.length * lineHeight)
    )
  );

  const mask = makeCanvas(width, height);
  const maskContext = mask.getContext("2d", { willReadFrequently: true });
  if (!maskContext) {
    return null;
  }
  maskContext.fillStyle = "#ffffff";
  maskContext.textBaseline = "alphabetic";
  maskContext.textAlign = "left";
  maskContext.font = `700 ${size}px ${FONT_CN}`;
  if ("letterSpacing" in maskContext) {
    (maskContext as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing =
      `${size * 0.04}px`;
  }

  const totalHeight = lines.length * size * lineHeight;
  const startY = (height - totalHeight) / 2 + size * 0.84;
  lines.forEach((line, index) => {
    maskContext.fillText(line, paddingX, startY + index * size * lineHeight);
  });

  const data = maskContext.getImageData(0, 0, width, height).data;
  const cell = Math.max(5, Math.round(size * 0.038));
  const cellStep = Math.max(1, Math.floor(cell / 4));

  let minX = width;
  let maxX = 0;
  let minY = height;
  let maxY = 0;

  context.fillStyle = "rgba(255,255,255,0.95)";
  for (let y = 0; y < height; y += cell) {
    const yEnd = Math.min(y + cell, height);
    for (let x = 0; x < width; x += cell) {
      let sum = 0;
      let count = 0;
      const xEnd = Math.min(x + cell, width);
      for (let sampleY = y; sampleY < yEnd; sampleY += cellStep) {
        const row = sampleY * width;
        for (let sampleX = x; sampleX < xEnd; sampleX += cellStep) {
          sum += data[(row + sampleX) * 4 + 3];
          count += 1;
        }
      }
      if (!count) {
        continue;
      }
      const coverage = sum / (count * 255);
      if (coverage < 0.02) {
        continue;
      }
      const radius = Math.sqrt(coverage) * cell * 0.58;
      if (radius < 0.6) {
        continue;
      }
      const centerX = x + cell / 2;
      const centerY = y + cell / 2;
      if (centerX < minX) {
        minX = centerX;
      }
      if (centerX > maxX) {
        maxX = centerX;
      }
      if (centerY < minY) {
        minY = centerY;
      }
      if (centerY > maxY) {
        maxY = centerY;
      }
      context.beginPath();
      context.arc(centerX, centerY, radius, 0, Math.PI * 2);
      context.fill();
    }
  }

  if (maxX <= minX || maxY <= minY) {
    return null;
  }

  return {
    charCount: Array.from(text).length,
    dpr,
    maxX,
    maxY,
    minX,
    minY
  };
}

export function PortalTitleCanvas() {
  const ref = useRef<HTMLCanvasElement>(null);
  const snapshotRef = useRef<HTMLCanvasElement | null>(null);
  const boundsRef = useRef<TitleBounds | null>(null);
  const pointerRef = useRef<{ x: number; y: number } | null>(null);
  const frameRef = useRef(0);
  const paintFrameRef = useRef(0);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) {
      return;
    }

    const context = canvas.getContext("2d");

    const copySnapshot = () => {
      if (!context || !canvas.width || !canvas.height) {
        return;
      }
      const snapshot = makeCanvas(canvas.width, canvas.height);
      snapshot.getContext("2d")?.drawImage(canvas, 0, 0);
      snapshotRef.current = snapshot;
    };

    const paint = () => {
      const bounds = paintTitle(canvas);
      boundsRef.current = bounds;
      copySnapshot();
    };

    const stopAnimation = () => {
      cancelAnimationFrame(frameRef.current);
      frameRef.current = 0;
    };

    const restoreBase = () => {
      if (!context || !snapshotRef.current) {
        return;
      }
      context.clearRect(0, 0, canvas.width, canvas.height);
      context.drawImage(snapshotRef.current, 0, 0);
    };

    const drawDistorted = (time: number) => {
      const snapshot = snapshotRef.current;
      const bounds = boundsRef.current;
      const pointer = pointerRef.current;
      if (!context || !snapshot || !bounds || !pointer) {
        return;
      }

      const radius = Math.max(42, (bounds.maxY - bounds.minY) * 0.24);
      const regionX = pointer.x - radius;
      const regionY = pointer.y - radius;
      const regionSize = radius * 2;

      restoreBase();
      context.save();
      context.beginPath();
      context.arc(pointer.x, pointer.y, radius, 0, Math.PI * 2);
      context.clip();
      context.clearRect(regionX, regionY, regionSize, regionSize);

      const sliceHeight = Math.max(3, Math.round(6 * bounds.dpr));
      for (let y = regionY; y < regionY + regionSize; y += sliceHeight) {
        const height = Math.min(sliceHeight, regionY + regionSize - y);
        const offset =
          Math.sin(time * 0.018 + y * 0.11) * radius * 0.18 +
          Math.sin(time * 0.036 + y * 0.03) * radius * 0.08;
        context.drawImage(
          snapshot,
          regionX,
          y,
          regionSize,
          height,
          regionX + offset,
          y,
          regionSize,
          height
        );
      }

      context.fillStyle = "rgba(255,255,255,0.82)";
      for (let i = 0; i < 160; i += 1) {
        const angle = Math.random() * Math.PI * 2;
        const distance = Math.random() * radius;
        const x = pointer.x + Math.cos(angle) * distance;
        const y = pointer.y + Math.sin(angle) * distance;
        const dotRadius = 0.6 + Math.random() * 2.1;
        context.beginPath();
        context.arc(x, y, dotRadius, 0, Math.PI * 2);
        context.fill();
      }
      context.restore();
    };

    const animate = (time: number) => {
      if (pointerRef.current === null) {
        frameRef.current = 0;
        return;
      }
      drawDistorted(time);
      frameRef.current = requestAnimationFrame(animate);
    };

    const startAnimation = () => {
      if (!frameRef.current) {
        frameRef.current = requestAnimationFrame(animate);
      }
    };

    const updateHover = (event: PointerEvent) => {
      const bounds = boundsRef.current;
      if (!bounds) {
        return;
      }
      const rect = canvas.getBoundingClientRect();
      if (!rect.width || !rect.height) {
        return;
      }
      const x = (event.clientX - rect.left) * (canvas.width / rect.width);
      const y = (event.clientY - rect.top) * (canvas.height / rect.height);
      const inside =
        x >= bounds.minX && x <= bounds.maxX && y >= bounds.minY && y <= bounds.maxY;
      const nextPointer = inside ? { x, y } : null;
      const wasActive = pointerRef.current !== null;

      pointerRef.current = nextPointer;

      if (nextPointer === null) {
        if (wasActive) {
          stopAnimation();
          restoreBase();
        }
      } else {
        startAnimation();
      }
    };

    const onPointerLeave = () => {
      pointerRef.current = null;
      stopAnimation();
      restoreBase();
    };

    const schedulePaint = () => {
      stopAnimation();
      pointerRef.current = null;
      cancelAnimationFrame(paintFrameRef.current);
      paintFrameRef.current = requestAnimationFrame(paint);
    };

    schedulePaint();
    window.addEventListener("resize", schedulePaint);
    canvas.addEventListener("pointermove", updateHover);
    canvas.addEventListener("pointerleave", onPointerLeave);
    if (document.fonts?.ready) {
      document.fonts.ready.then(schedulePaint).catch(() => undefined);
    }

    return () => {
      cancelAnimationFrame(frameRef.current);
      cancelAnimationFrame(paintFrameRef.current);
      window.removeEventListener("resize", schedulePaint);
      canvas.removeEventListener("pointermove", updateHover);
      canvas.removeEventListener("pointerleave", onPointerLeave);
    };
  }, []);

  return <canvas aria-hidden="true" className="portal-title-canvas" ref={ref} />;
}