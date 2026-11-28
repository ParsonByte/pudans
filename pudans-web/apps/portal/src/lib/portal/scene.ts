import * as THREE from "three";

type PortalProgress = {
  current: number;
  target: number;
};

type PortalProgressRef = {
  current: PortalProgress | null;
};

type PortalSceneOptions = {
  canvas: HTMLCanvasElement;
  progressRef: PortalProgressRef;
  root: HTMLElement;
};

export type PortalSceneHandle = {
  dispose: () => void;
  start: () => void;
};

type CardData = {
  idx: string;
  title: string;
  sub: string;
  icon: string;
};

type SceneAssets = {
  cards: THREE.Texture[];
  dot: THREE.Texture;
  glass: THREE.Texture;
  glow: THREE.Texture;
};

type StageDefinition = {
  build: string;
  ry: number;
  x: number;
  y: number;
  z: number;
};

type StageRuntime = {
  group: THREE.Group;
  home: THREE.Vector3;
  homeRy: number;
  local: number;
  mats: THREE.Material[];
  phase: number;
  z: number;
};

const DEG = Math.PI / 180;
const TAU = Math.PI * 2;
const FONT_CN =
  '"Noto Sans SC","PingFang SC","Hiragino Sans GB","Microsoft YaHei",system-ui,sans-serif';
const FONT_EN = '"Space Grotesk","Helvetica Neue",Arial,sans-serif';
const START_Z = 14;
const END_Z = -158;
const FINALE_Z = -180;
const FINALE_X = 0;
const FINALE_Y = 0.5;
const STAGE_COUNT = 6;
const STAGE_DEF: StageDefinition[] = [
  { z: -28, x: -2.4, y: 0.7, ry: 0.1, build: "ai" },
  { z: -52, x: 1.8, y: 0.2, ry: -0.12, build: "data" },
  { z: -76, x: -1.6, y: 0.15, ry: 0.12, build: "hub" },
  { z: -100, x: 1.5, y: 0.25, ry: -0.14, build: "graph" },
  { z: -124, x: -1.4, y: 0.05, ry: 0.1, build: "connectors" },
  { z: -148, x: 0, y: 0.2, ry: 0, build: "library" }
];
const LOOK_KEYS = [0, 0.2, 0.4, 0.6, 0.8, 1];
const LOOK_X = [-0.4, -1.5, 1.3, -1.1, 1.2, 0];
const LOOK_Y = [1.6, 2.7, 2.4, 2, 2.3, 1.7];
const CHAPTERS = [
  { i: "01", t: "AI 工具矩阵", p: 0.14 },
  { i: "02", t: "数据中心", p: 0.28 },
  { i: "03", t: "应用集成枢纽", p: 0.42 },
  { i: "04", t: "知识图谱", p: 0.56 },
  { i: "05", t: "连接器矩阵", p: 0.7 },
  { i: "06", t: "知识库", p: 0.84 }
];
const CARD_DATA: CardData[] = [
  { idx: "01", title: "AI Agent", sub: "智能任务编排", icon: "nodes" },
  { idx: "02", title: "RAG 检索", sub: "企业知识召回", icon: "grid" },
  { idx: "03", title: "多模态", sub: "图文音视频解析", icon: "wave" },
  { idx: "04", title: "工作流", sub: "自动化流水线", icon: "flow" },
  { idx: "05", title: "数据洞察", sub: "指标实时归因", icon: "bars" },
  { idx: "06", title: "语音引擎", sub: "转写 · 合成", icon: "ring" },
  { idx: "07", title: "模型路由", sub: "多模型统一调度", icon: "cube" }
];

const clamp = (value: number, min: number, max: number) =>
  value < min ? min : value > max ? max : value;
const clamp01 = (value: number) => (value < 0 ? 0 : value > 1 ? 1 : value);
const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
const smoothstep = (a: number, b: number, x: number) => {
  const t = clamp01((x - a) / (b - a));
  return t * t * (3 - 2 * t);
};

function sampleLook(values: number[], progress: number) {
  for (let i = 0; i < LOOK_KEYS.length - 1; i += 1) {
    if (progress <= LOOK_KEYS[i + 1]) {
      const ratio = (progress - LOOK_KEYS[i]) / (LOOK_KEYS[i + 1] - LOOK_KEYS[i]);
      const eased = ratio * ratio * (3 - 2 * ratio);
      return values[i] + (values[i + 1] - values[i]) * eased;
    }
  }
  return values[values.length - 1];
}

function makeCanvas(width: number, height: number, readable = false) {
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  if (readable) {
    canvas.getContext("2d", { willReadFrequently: true });
  }
  return canvas;
}

function tex(canvas: HTMLCanvasElement) {
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 4;
  texture.needsUpdate = true;
  return texture;
}

function setLetterSpacing(context: CanvasRenderingContext2D, value: string) {
  if ("letterSpacing" in context) {
    (context as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = value;
  }
}

function roundRectPath(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
) {
  const resolved = Math.min(radius, width / 2, height / 2);
  context.beginPath();
  context.moveTo(x + resolved, y);
  context.lineTo(x + width - resolved, y);
  context.arcTo(x + width, y, x + width, y + resolved, resolved);
  context.lineTo(x + width, y + height - resolved);
  context.arcTo(x + width, y + height, x + width - resolved, y + height, resolved);
  context.lineTo(x + resolved, y + height);
  context.arcTo(x, y + height, x, y + height - resolved, resolved);
  context.lineTo(x, y + resolved);
  context.arcTo(x, y, x + resolved, y, resolved);
  context.closePath();
}

function fitFont(
  context: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  size: number,
  family: string,
  weight = 700
) {
  let resolved = size;
  for (let i = 0; i < 40; i += 1) {
    context.font = `${weight} ${resolved}px ${family}`;
    if (context.measureText(text).width <= maxWidth || resolved <= 8) {
      break;
    }
    resolved *= 0.94;
  }
  return resolved;
}

function glowTexture(size = 128) {
  const canvas = makeCanvas(size, size);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  const gradient = context.createRadialGradient(
    size / 2,
    size / 2,
    0,
    size / 2,
    size / 2,
    size / 2
  );
  gradient.addColorStop(0, "rgba(255,255,255,0.95)");
  gradient.addColorStop(0.18, "rgba(255,255,255,0.55)");
  gradient.addColorStop(0.42, "rgba(255,255,255,0.18)");
  gradient.addColorStop(1, "rgba(255,255,255,0)");
  context.fillStyle = gradient;
  context.fillRect(0, 0, size, size);
  return tex(canvas);
}

function dotTexture(size = 64) {
  const canvas = makeCanvas(size, size);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  const gradient = context.createRadialGradient(
    size / 2,
    size / 2,
    0,
    size / 2,
    size / 2,
    size / 2
  );
  gradient.addColorStop(0, "rgba(255,255,255,1)");
  gradient.addColorStop(0.62, "rgba(255,255,255,0.92)");
  gradient.addColorStop(1, "rgba(255,255,255,0)");
  context.fillStyle = gradient;
  context.beginPath();
  context.arc(size / 2, size / 2, size / 2, 0, TAU);
  context.fill();
  return tex(canvas);
}

function makeGlassTexture() {
  const width = 1024;
  const height = 634;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  context.clearRect(0, 0, width, height);
  context.save();
  roundRectPath(context, 8, 8, width - 16, height - 16, 22);
  context.clip();
  const gradient = context.createLinearGradient(0, 0, width, height);
  gradient.addColorStop(0, "rgba(255,255,255,0.28)");
  gradient.addColorStop(0.46, "rgba(255,255,255,0.08)");
  gradient.addColorStop(1, "rgba(255,255,255,0.18)");
  context.fillStyle = gradient;
  context.fillRect(8, 8, width - 16, height - 16);
  context.fillStyle = "rgba(255,255,255,0.5)";
  context.fillRect(8, 8, width - 16, 3);
  context.strokeStyle = "rgba(255,255,255,0.08)";
  context.lineWidth = 1;
  for (let x = 48; x < width - 24; x += 64) {
    context.beginPath();
    context.moveTo(x, 28);
    context.lineTo(x, height - 28);
    context.stroke();
  }
  for (let y = 44; y < height - 24; y += 54) {
    context.beginPath();
    context.moveTo(24, y);
    context.lineTo(width - 24, y);
    context.stroke();
  }
  context.restore();
  roundRectPath(context, 8, 8, width - 16, height - 16, 22);
  context.strokeStyle = "rgba(255,255,255,0.48)";
  context.lineWidth = 2;
  context.stroke();
  return tex(canvas);
}

function drawIcon(
  context: CanvasRenderingContext2D,
  kind: string,
  centerX: number,
  centerY: number,
  size: number
) {
  context.save();
  context.translate(centerX, centerY);
  context.lineWidth = 3;
  context.strokeStyle = "rgba(255,255,255,0.9)";
  context.fillStyle = "rgba(255,255,255,0.9)";

  if (kind === "nodes") {
    const points = [
      [-size * 0.42, size * 0.22],
      [0, -size * 0.42],
      [size * 0.42, size * 0.22],
      [0, size * 0.34]
    ];
    context.strokeStyle = "rgba(255,255,255,0.45)";
    context.beginPath();
    points.forEach((point, index) => {
      const next = points[(index + 1) % 4];
      context.moveTo(point[0], point[1]);
      context.lineTo(next[0], next[1]);
    });
    context.moveTo(points[0][0], points[0][1]);
    context.lineTo(points[2][0], points[2][1]);
    context.stroke();
    points.forEach((point, index) => {
      context.beginPath();
      context.arc(point[0], point[1], index === 1 ? size * 0.13 : size * 0.08, 0, TAU);
      context.strokeStyle = "rgba(255,255,255,0.85)";
      context.stroke();
    });
  } else if (kind === "grid") {
    for (let row = -1; row <= 1; row += 1) {
      for (let column = -1; column <= 1; column += 1) {
        context.beginPath();
        context.arc(column * size * 0.36, row * size * 0.36, size * 0.09, 0, TAU);
        context.strokeStyle = `rgba(255,255,255,${0.35 + (row + column + 2) * 0.12})`;
        context.stroke();
      }
    }
    context.strokeStyle = "rgba(255,255,255,0.85)";
    context.strokeRect(-size * 0.46, -size * 0.46, size * 0.92, size * 0.92);
  } else if (kind === "wave") {
    context.beginPath();
    for (let i = 0; i <= 44; i += 1) {
      const t = i / 44;
      const x = -size * 0.5 + t * size;
      const y = Math.sin(t * TAU * 1.5 + 0.4) * size * 0.22;
      if (i === 0) {
        context.moveTo(x, y);
      } else {
        context.lineTo(x, y);
      }
    }
    context.stroke();
    context.strokeStyle = "rgba(255,255,255,0.3)";
    context.beginPath();
    context.moveTo(-size * 0.5, 0);
    context.lineTo(size * 0.5, 0);
    context.stroke();
  } else if (kind === "flow") {
    context.strokeStyle = "rgba(255,255,255,0.75)";
    for (let i = 0; i < 2; i += 1) {
      const y = -size * 0.28 + i * size * 0.56;
      context.strokeRect(-size * 0.46, y - size * 0.12, size * 0.4, size * 0.24);
      context.strokeRect(size * 0.06, y - size * 0.12, size * 0.4, size * 0.24);
      context.beginPath();
      context.moveTo(-size * 0.02, y);
      context.lineTo(size * 0.02, y);
      context.stroke();
    }
  } else if (kind === "bars") {
    const heights = [0.34, 0.62, 0.44, 0.82, 0.55];
    heights.forEach((height, index) => {
      const x = -size * 0.42 + index * size * 0.21;
      context.strokeStyle = `rgba(255,255,255,${0.45 + height * 0.6})`;
      context.strokeRect(x, size * 0.38 - height * size, size * 0.12, height * size);
    });
  } else if (kind === "ring") {
    context.strokeStyle = "rgba(255,255,255,0.8)";
    context.beginPath();
    context.arc(0, 0, size * 0.4, 0, TAU);
    context.stroke();
    context.beginPath();
    context.arc(0, 0, size * 0.24, 0, TAU);
    context.stroke();
    for (let i = 0; i < 16; i += 1) {
      const angle = (i / 16) * TAU;
      context.beginPath();
      context.moveTo(Math.cos(angle) * size * 0.43, Math.sin(angle) * size * 0.43);
      context.lineTo(Math.cos(angle) * size * 0.5, Math.sin(angle) * size * 0.5);
      context.stroke();
    }
  } else if (kind === "cube") {
    const radius = size * 0.46;
    const top = [
      [0, -radius],
      [radius * 0.87, -radius * 0.5],
      [0, 0],
      [-radius * 0.87, -radius * 0.5]
    ];
    const bottom = [
      [0, 0],
      [radius * 0.87, radius * 0.5],
      [0, radius],
      [-radius * 0.87, radius * 0.5]
    ];
    context.strokeStyle = "rgba(255,255,255,0.8)";
    context.beginPath();
    top.forEach((point, index) => {
      if (index === 0) {
        context.moveTo(point[0], point[1]);
      } else {
        context.lineTo(point[0], point[1]);
      }
    });
    context.closePath();
    context.stroke();
    context.beginPath();
    bottom.forEach((point, index) => {
      if (index === 0) {
        context.moveTo(point[0], point[1]);
      } else {
        context.lineTo(point[0], point[1]);
      }
    });
    context.closePath();
    context.stroke();
    context.strokeStyle = "rgba(255,255,255,0.4)";
    context.beginPath();
    context.moveTo(-radius * 0.87, -radius * 0.5);
    context.lineTo(-radius * 0.87, radius * 0.5);
    context.moveTo(radius * 0.87, -radius * 0.5);
    context.lineTo(radius * 0.87, radius * 0.5);
    context.stroke();
  }

  context.restore();
}

function makeCardTexture(data: CardData) {
  const width = 560;
  const height = 720;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }

  roundRectPath(context, 5, 5, width - 10, height - 10, 26);
  const cardGradient = context.createLinearGradient(0, 0, width, height);
  cardGradient.addColorStop(0, "rgba(255,255,255,0.2)");
  cardGradient.addColorStop(0.5, "rgba(255,255,255,0.06)");
  cardGradient.addColorStop(1, "rgba(255,255,255,0.16)");
  context.fillStyle = cardGradient;
  context.fill();
  context.lineWidth = 2;
  context.strokeStyle = "rgba(255,255,255,0.82)";
  context.stroke();
  context.strokeStyle = "rgba(255,255,255,0.1)";
  context.lineWidth = 1;
  for (let x = 38; x < width - 38; x += 48) {
    context.beginPath();
    context.moveTo(x, 30);
    context.lineTo(x, height - 30);
    context.stroke();
  }
  for (let y = 100; y < height - 40; y += 52) {
    context.beginPath();
    context.moveTo(24, y);
    context.lineTo(width - 24, y);
    context.stroke();
  }

  context.fillStyle = "rgba(255,255,255,0.45)";
  context.font = `500 20px ${FONT_EN}`;
  setLetterSpacing(context, "4px");
  context.fillText(`${data.idx} / MODULE`, 38, 72);
  setLetterSpacing(context, "0px");

  context.strokeStyle = "rgba(255,255,255,0.3)";
  context.beginPath();
  context.moveTo(width - 120, 60);
  context.lineTo(width - 40, 60);
  context.stroke();
  context.beginPath();
  context.arc(width - 54, 60, 4, 0, TAU);
  context.fillStyle = "#ffffff";
  context.fill();

  drawIcon(context, data.icon, width / 2, 226, 116);

  const size = fitFont(context, data.title, width - 78, 64, FONT_CN, 700);
  context.fillStyle = "#ffffff";
  context.font = `700 ${size}px ${FONT_CN}`;
  context.fillText(data.title, 38, 432);

  context.fillStyle = "rgba(255,255,255,0.62)";
  context.font = `400 25px ${FONT_CN}`;
  context.fillText(data.sub, 39, 482);

  context.strokeStyle = "rgba(255,255,255,0.18)";
  context.lineWidth = 1;
  context.beginPath();
  context.moveTo(38, 524);
  context.lineTo(width - 38, 524);
  context.stroke();

  const bars: Array<[number, number]> = [
    [180, 0.72],
    [330, 0.45],
    [250, 0.9]
  ];
  let barY = 566;
  bars.forEach(([barWidth, strength]) => {
    context.strokeStyle = "rgba(255,255,255,0.14)";
    context.strokeRect(38, barY, width - 76, 6);
    context.fillStyle = `rgba(255,255,255,${0.28 + strength * 0.5})`;
    context.fillRect(38, barY, (width - 76) * (barWidth / 520), 6);
    barY += 26;
  });

  context.fillStyle = "rgba(255,255,255,0.32)";
  context.font = `400 18px ${FONT_EN}`;
  setLetterSpacing(context, "2px");
  context.fillText("STATUS · RUNNING", 38, 682);
  setLetterSpacing(context, "0px");

  return tex(canvas);
}

function makeHudTexture() {
  const width = 640;
  const height = 200;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  const texture = tex(canvas);
  const spark = new Array(26).fill(0.5);
  let value = 1284306;
  let accumulator = 0;

  const draw = () => {
    context.clearRect(0, 0, width, height);
    context.fillStyle = "rgba(255,255,255,0.45)";
    context.font = `600 17px ${FONT_EN}`;
    setLetterSpacing(context, "7px");
    context.fillText("EVENTS / SEC", 0, 26);
    setLetterSpacing(context, "0px");
    context.fillStyle = "#ffffff";
    context.font = `700 62px ${FONT_EN}`;
    context.fillText(value.toLocaleString("en-US"), 0, 100);
    context.fillStyle = "rgba(255,255,255,0.4)";
    context.font = `500 15px ${FONT_EN}`;
    setLetterSpacing(context, "3px");
    context.fillText("PUDANS DATA CORE", 2, 142);
    setLetterSpacing(context, "0px");

    const startX = 360;
    const startY = 30;
    const chartWidth = 280;
    const chartHeight = 100;
    context.strokeStyle = "rgba(255,255,255,0.15)";
    context.lineWidth = 1;
    context.beginPath();
    context.moveTo(startX, startY + chartHeight);
    context.lineTo(startX + chartWidth, startY + chartHeight);
    context.stroke();
    context.strokeStyle = "rgba(255,255,255,0.85)";
    context.lineWidth = 2;
    context.beginPath();
    spark.forEach((point, index) => {
      const x = startX + (index / (spark.length - 1)) * chartWidth;
      const y = startY + chartHeight - point * chartHeight;
      if (index === 0) {
        context.moveTo(x, y);
      } else {
        context.lineTo(x, y);
      }
    });
    context.stroke();
    context.fillStyle = "rgba(255,255,255,0.9)";
    const last = spark[spark.length - 1];
    context.beginPath();
    context.arc(startX + chartWidth, startY + chartHeight - last * chartHeight, 4, 0, TAU);
    context.fill();
    texture.needsUpdate = true;
  };

  draw();

  return {
    texture,
    tick(dt: number) {
      accumulator += dt;
      if (accumulator < 0.12) {
        return;
      }
      accumulator = 0;
      value += Math.floor(Math.random() * 4200 - 900);
      if (value < 0) {
        value = 0;
      }
      spark.push(0.25 + Math.random() * 0.7);
      spark.shift();
      draw();
    }
  };
}

function lineMaterial(opacity: number) {
  return new THREE.LineBasicMaterial({
    color: 0xffffff,
    depthWrite: false,
    opacity,
    transparent: true
  });
}

function circlePoints(radius: number, segments: number, plane: "xy" | "xz") {
  const points: THREE.Vector3[] = [];
  for (let i = 0; i <= segments; i += 1) {
    const angle = (i / segments) * TAU;
    if (plane === "xz") {
      points.push(new THREE.Vector3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
    } else {
      points.push(new THREE.Vector3(Math.cos(angle) * radius, Math.sin(angle) * radius, 0));
    }
  }
  return points;
}

function rectanglePoints(width: number, height: number) {
  const halfWidth = width / 2;
  const halfHeight = height / 2;
  return [
    new THREE.Vector3(-halfWidth, halfHeight, 0),
    new THREE.Vector3(halfWidth, halfHeight, 0),
    new THREE.Vector3(halfWidth, -halfHeight, 0),
    new THREE.Vector3(-halfWidth, -halfHeight, 0),
    new THREE.Vector3(-halfWidth, halfHeight, 0)
  ];
}

function outline(width: number, height: number, opacity: number) {
  return new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(rectanglePoints(width, height)),
    lineMaterial(opacity)
  );
}

function collectFade(root: THREE.Object3D) {
  const materials = new Set<THREE.Material>();
  root.traverse((object) => {
    if (object instanceof THREE.Mesh || object instanceof THREE.Line || object instanceof THREE.Points) {
      const list = Array.isArray(object.material) ? object.material : [object.material];
      list.forEach((material) => materials.add(material));
    }
  });
  const result: THREE.Material[] = [];
  materials.forEach((material) => {
    const stored = material.userData.base;
    if (stored === undefined) {
      material.userData.base = material.opacity;
    }
    material.transparent = true;
    result.push(material);
  });
  return result;
}

function createAssets(): SceneAssets {
  return {
    cards: CARD_DATA.map((card) => makeCardTexture(card)),
    dot: dotTexture(),
    glass: makeGlassTexture(),
    glow: glowTexture(256)
  };
}

function createGlassPanel(width: number, height: number, assets: SceneAssets) {
  const group = new THREE.Group();
  const face = new THREE.Mesh(
    new THREE.PlaneGeometry(width, height),
    new THREE.MeshBasicMaterial({
      map: assets.glass,
      opacity: 0.92,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide
    })
  );
  group.add(face);
  return group;
}

function createGlassBox(width: number, height: number, depth: number) {
  const group = new THREE.Group();
  const body = new THREE.Mesh(
    new THREE.BoxGeometry(width, height, depth),
    new THREE.MeshBasicMaterial({
      color: 0xffffff,
      opacity: 0.13,
      transparent: true,
      depthWrite: false
    })
  );
  group.add(body);
  const edges = new THREE.LineSegments(
    new THREE.EdgesGeometry(body.geometry),
    lineMaterial(0.58)
  );
  group.add(edges);
  return group;
}

function buildAIMatrix(assets: SceneAssets) {
  const group = new THREE.Group();
  const positions = [
    { x: -6, y: 1.6, z: 0, ry: 0.24, rz: 0.05 },
    { x: -2.2, y: 3.4, z: -1.6, ry: 0.12, rz: -0.03 },
    { x: 2.2, y: 2.1, z: 0.8, ry: -0.1, rz: 0.04 },
    { x: 6.1, y: 3.7, z: -2.4, ry: -0.26, rz: -0.05 },
    { x: -4, y: -2.6, z: -1, ry: 0.2, rz: -0.04 },
    { x: 0.9, y: -3.3, z: 1.3, ry: -0.06, rz: 0.03 },
    { x: 5.6, y: -1.5, z: -2, ry: -0.22, rz: 0.06 }
  ];
  const cards: THREE.Mesh[] = [];
  const geometry = new THREE.PlaneGeometry(3.5, 4.5);

  positions.forEach((position, index) => {
    const mesh = new THREE.Mesh(
      geometry,
      new THREE.MeshBasicMaterial({
        map: assets.cards[index],
        opacity: 1,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide
      })
    );
    mesh.position.set(position.x, position.y, position.z);
    mesh.rotation.set(0, position.ry, position.rz);
    mesh.userData.baseY = position.y;
    mesh.userData.baseRz = position.rz;
    mesh.userData.phase = index * 1.13;
    group.add(mesh);
    cards.push(mesh);
  });

  const connections = new THREE.LineSegments(
    new THREE.BufferGeometry().setFromPoints(
      [
        [0, 1],
        [1, 2],
        [2, 3],
        [0, 4],
        [4, 5],
        [5, 6],
        [2, 5],
        [3, 4]
      ].flatMap(([from, to]) => [cards[from].position.clone(), cards[to].position.clone()])
    ),
    lineMaterial(0.22)
  );
  group.add(connections);

  const gridPoints: THREE.Vector3[] = [];
  for (let x = -9; x <= 9; x += 1.5) {
    gridPoints.push(new THREE.Vector3(x, -4.6, -4.6), new THREE.Vector3(x, 5.6, -4.6));
  }
  for (let y = -4.6; y <= 5.6; y += 1.5) {
    gridPoints.push(new THREE.Vector3(-9, y, -4.6), new THREE.Vector3(9, y, -4.6));
  }
  group.add(
    new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(gridPoints), lineMaterial(0.06))
  );

  group.userData.tick = (time: number) => {
    cards.forEach((card) => {
      card.position.y =
        (card.userData.baseY as number) +
        Math.sin(time * 0.7 + (card.userData.phase as number)) * 0.16;
      card.rotation.z =
        (card.userData.baseRz as number) +
        Math.sin(time * 0.5 + (card.userData.phase as number)) * 0.03;
    });
  };

  return group;
}

function createDome(radius: number) {
  const group = new THREE.Group();
  const shell = new THREE.Mesh(
    new THREE.SphereGeometry(radius, 32, 18, 0, TAU, 0, Math.PI / 2),
    new THREE.MeshBasicMaterial({
      color: 0xffffff,
      opacity: 0.035,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide
    })
  );
  group.add(shell);
  const material = lineMaterial(0.2);
  for (let i = 0; i < 8; i += 1) {
    const longitude = (i / 8) * Math.PI;
    const points: THREE.Vector3[] = [];
    for (let j = 0; j <= 32; j += 1) {
      const latitude = (j / 32) * (Math.PI / 2);
      points.push(
        new THREE.Vector3(
          Math.cos(latitude) * Math.sin(longitude) * radius,
          Math.sin(latitude) * radius,
          Math.cos(latitude) * Math.cos(longitude) * radius
        )
      );
    }
    group.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), material));
  }
  for (let i = 1; i <= 4; i += 1) {
    const latitude = (i / 5) * (Math.PI / 2);
    const ringRadius = Math.cos(latitude) * radius;
    const y = Math.sin(latitude) * radius;
    const points = circlePoints(ringRadius, 64, "xz").map(
      (point) => new THREE.Vector3(point.x, y, point.z)
    );
    group.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), material));
  }
  return group;
}

function buildDataCore(assets: SceneAssets) {
  const group = new THREE.Group();
  const hud = makeHudTexture();
  const ringGroup = new THREE.Group();
  ringGroup.position.y = 2.05;

  [
    [2.2, 0.3],
    [3.1, 0.34],
    [4, 0.3]
  ].forEach(([radius, tilt], index) => {
    const line = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(circlePoints(radius, 96, "xz")),
      lineMaterial(0.68 - index * 0.1)
    );
    line.rotation.x = tilt;
    line.rotation.z = index * 0.4;
    ringGroup.add(line);
  });

  const tickPoints: THREE.Vector3[] = [];
  for (let i = 0; i < 72; i += 1) {
    const angle = (i / 72) * TAU;
    const inner = 4;
    const outer = i % 6 === 0 ? 4.34 : 4.18;
    tickPoints.push(
      new THREE.Vector3(Math.cos(angle) * inner, 0, Math.sin(angle) * inner),
      new THREE.Vector3(Math.cos(angle) * outer, 0, Math.sin(angle) * outer)
    );
  }
  const tickRing = new THREE.LineSegments(
    new THREE.BufferGeometry().setFromPoints(tickPoints),
    lineMaterial(0.3)
  );
  tickRing.rotation.x = 0.3;
  ringGroup.add(tickRing);

  const dashPoints: THREE.Vector3[] = [];
  for (let i = 0; i < 56; i += 1) {
    const start = (i / 56) * TAU;
    const end = start + 0.05;
    dashPoints.push(
      new THREE.Vector3(Math.cos(start) * 4.95, 0, Math.sin(start) * 4.95),
      new THREE.Vector3(Math.cos(end) * 4.95, 0, Math.sin(end) * 4.95)
    );
  }
  const dashRing = new THREE.LineSegments(
    new THREE.BufferGeometry().setFromPoints(dashPoints),
    lineMaterial(0.42)
  );
  dashRing.rotation.x = 0.5;
  ringGroup.add(dashRing);

  const sweep = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(0, 0, 0),
      new THREE.Vector3(4, 0, 0)
    ]),
    lineMaterial(0.55)
  );
  sweep.rotation.x = 0.3;
  ringGroup.add(sweep);
  group.add(ringGroup);

  const dome = createDome(4.6);
  dome.position.y = -1.55;
  group.add(dome);

  const bars: THREE.LineSegments[] = [];
  const barGeometry = new THREE.BufferGeometry().setFromPoints([
    new THREE.Vector3(-0.5, 0, 0),
    new THREE.Vector3(0.5, 0, 0),
    new THREE.Vector3(0.5, 0, 0),
    new THREE.Vector3(0.5, 1, 0),
    new THREE.Vector3(0.5, 1, 0),
    new THREE.Vector3(-0.5, 1, 0),
    new THREE.Vector3(-0.5, 1, 0),
    new THREE.Vector3(-0.5, 0, 0)
  ]);
  for (let i = 0; i < 12; i += 1) {
    const bar = new THREE.LineSegments(barGeometry, lineMaterial(0.5));
    bar.position.set(-3.2 + (i / 11) * 6.4, -3.05, -0.7);
    bar.scale.set(0.32, 1.2, 1);
    group.add(bar);
    bars.push(bar);
  }

  const hudSprite = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: hud.texture,
      opacity: 1,
      transparent: true,
      depthWrite: false
    })
  );
  hudSprite.position.set(0, 4.95, 0.6);
  hudSprite.scale.set(5.2, 1.63, 1);
  group.add(hudSprite);

  group.userData.tick = (time: number, dt: number, alpha: number) => {
    ringGroup.rotation.y = time * 0.1;
    dashRing.rotation.y = -time * 0.55;
    sweep.rotation.y = time * 1.6;
    bars.forEach((bar, index) => {
      bar.scale.y =
        0.9 + Math.abs(Math.sin(time * 0.9 + index * 0.62)) * 1.9 + (index % 3 === 0 ? 0.3 : 0);
    });
    if (alpha > 0.25) {
      hud.tick(dt);
    }
  };

  return group;
}

function makeAppTileTexture(label: string, sub: string, icon: string) {
  const width = 640;
  const height = 392;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  context.clearRect(0, 0, width, height);
  roundRectPath(context, 4, 4, width - 8, height - 8, 24);
  const gradient = context.createLinearGradient(0, 0, width, height);
  gradient.addColorStop(0, "rgba(255,255,255,0.22)");
  gradient.addColorStop(0.5, "rgba(255,255,255,0.06)");
  gradient.addColorStop(1, "rgba(255,255,255,0.16)");
  context.fillStyle = gradient;
  context.fill();
  context.lineWidth = 2;
  context.strokeStyle = "rgba(255,255,255,0.64)";
  context.stroke();
  context.strokeStyle = "rgba(255,255,255,0.1)";
  context.lineWidth = 1;
  for (let x = 32; x < width - 24; x += 54) {
    context.beginPath();
    context.moveTo(x, 26);
    context.lineTo(x, height - 26);
    context.stroke();
  }
  drawIcon(context, icon, 82, 90, 58);
  context.fillStyle = "rgba(255,255,255,0.92)";
  context.font = `700 42px ${FONT_CN}`;
  context.fillText(label, 34, 218);
  context.fillStyle = "rgba(255,255,255,0.48)";
  context.font = `500 18px ${FONT_EN}`;
  setLetterSpacing(context, "5px");
  context.fillText(sub, 36, 260);
  setLetterSpacing(context, "0px");
  context.fillStyle = "rgba(255,255,255,0.28)";
  context.fillRect(36, 308, width - 72, 2);
  context.fillStyle = "rgba(255,255,255,0.7)";
  context.beginPath();
  context.arc(width - 50, 90, 4, 0, TAU);
  context.fill();
  return tex(canvas);
}

function buildIntegrationHub(assets: SceneAssets) {
  const group = new THREE.Group();
  const tiles: Array<{
    baseY: number;
    group: THREE.Group;
    linkMaterial: THREE.Material;
    phase: number;
  }> = [];
  const apps = [
    { label: "身份与访问", sub: "IAM", icon: "nodes" },
    { label: "文件仓储", sub: "REPOSITORY", icon: "cube" },
    { label: "通知中心", sub: "NOTIFICATION", icon: "wave" },
    { label: "消息中心", sub: "MESSAGE", icon: "flow" },
    { label: "任务调度", sub: "JOB", icon: "bars" },
    { label: "搜索服务", sub: "SEARCH", icon: "grid" }
  ];
  const xPositions = [-2.35, 0, 2.35];
  const yPositions = [1.2, -1.2];
  const hubRing = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(circlePoints(0.9, 80, "xy")),
    lineMaterial(0.3)
  );
  group.add(hubRing);
  const hubCore = new THREE.LineSegments(sphereWire(0.32, 16, 10), lineMaterial(0.56));
  group.add(hubCore);

  apps.forEach((app, index) => {
    const column = index % 3;
    const row = Math.floor(index / 3);
    const tile = new THREE.Group();
    const texture = makeAppTileTexture(app.label, app.sub, app.icon);
    const face = new THREE.Mesh(
      new THREE.PlaneGeometry(2.1, 1.28),
      new THREE.MeshBasicMaterial({
        map: texture,
        opacity: 0.94,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide
      })
    );
    tile.add(face);
    const depth = row === 0 ? -0.2 : 0.2;
    tile.position.set(xPositions[column], yPositions[row], depth);
    tile.userData.baseY = yPositions[row];
    tile.userData.phase = index * 0.7;
    group.add(tile);
    const linkMaterial = lineMaterial(0.16);
    group.add(
      new THREE.Line(
        new THREE.BufferGeometry().setFromPoints([
          new THREE.Vector3(0, 0, 0),
          new THREE.Vector3(xPositions[column], yPositions[row], depth)
        ]),
        linkMaterial
      )
    );
    tiles.push({
      baseY: yPositions[row],
      group: tile,
      linkMaterial,
      phase: index * 0.7
    });
  });

  group.userData.tick = (
    time: number,
    _dt: number,
    alpha: number,
    _progress: number,
    local: number
  ) => {
    hubRing.rotation.z = time * 0.22;
    hubCore.rotation.set(time * 0.4, time * 0.5, 0);
    tiles.forEach((tile, index) => {
      const reveal = smoothstep(0, 1, clamp01(local * 1.7 - index * 0.06));
      tile.group.position.y =
        tile.baseY + Math.sin(time * 0.6 + tile.phase) * 0.08 + (1 - reveal) * -0.35;
      tile.group.position.z = Math.sin(time * 0.4 + tile.phase) * 0.06;
      tile.group.scale.setScalar(0.88 + reveal * 0.12);
      tile.group.rotation.y = Math.sin(time * 0.35 + tile.phase) * 0.04;
      tile.linkMaterial.opacity = 0.16 * alpha * reveal;
    });
  };

  return group;
}

function buildKnowledgeGraph(assets: SceneAssets) {
  const group = new THREE.Group();
  const nodeMaterial = new THREE.MeshBasicMaterial({
    color: 0xffffff,
    opacity: 0.08,
    transparent: true,
    depthWrite: false
  });
  const nodeCount = 28;
  const nodes: Array<{ base: number; mesh: THREE.Mesh; phase: number }> = [];
  const nodePositions: THREE.Vector3[] = [];

  for (let i = 0; i < nodeCount; i += 1) {
    const angle = i * 2.399963;
    const vertical = 1 - 2 * ((i + 0.5) / nodeCount);
    const radius = 1.2 + (i % 5) * 0.36;
    const horizontal = Math.sqrt(Math.max(0, 1 - vertical * vertical));
    const position = new THREE.Vector3(
      Math.cos(angle) * horizontal * radius,
      vertical * radius * 0.8,
      Math.sin(angle) * horizontal * radius
    );
    const size = 0.1 + (i % 4) * 0.022;
    const mesh = new THREE.Mesh(new THREE.IcosahedronGeometry(size, 0), nodeMaterial.clone());
    mesh.position.copy(position);
    mesh.rotation.set(i * 0.7, i * 1.1, i * 0.3);
    const edges = new THREE.LineSegments(
      new THREE.EdgesGeometry(mesh.geometry),
      lineMaterial(0.5)
    );
    mesh.add(edges);
    group.add(mesh);
    nodes.push({ base: 0.8 + (i % 3) * 0.1, mesh, phase: i * 0.7 });
    nodePositions.push(position);
  }

  const center = new THREE.Mesh(
    new THREE.IcosahedronGeometry(0.24, 1),
    new THREE.MeshBasicMaterial({
      color: 0xffffff,
      opacity: 0.12,
      transparent: true,
      depthWrite: false
    })
  );
  const centerEdges = new THREE.LineSegments(
    new THREE.EdgesGeometry(center.geometry),
    lineMaterial(0.72)
  );
  center.add(centerEdges);
  group.add(center);

  const edgePairs: Array<[number, number]> = [];
  nodePositions.forEach((_position, index) => {
    edgePairs.push([-1, index]);
    if (index > 2 && index % 3 === 0) {
      edgePairs.push([index - 3, index]);
    }
    if (index % 5 === 0) {
      edgePairs.push([index, (index + 7) % nodeCount]);
    }
  });

  const lines: Array<{ line: THREE.Line; start: THREE.Vector3; end: THREE.Vector3 }> = [];
  edgePairs.forEach(([from, to]) => {
    const start = from < 0 ? new THREE.Vector3(0, 0, 0) : nodePositions[from];
    const end = nodePositions[to];
    const line = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([start, end]),
      lineMaterial(0.32)
    );
    line.geometry.setDrawRange(0, 0);
    group.add(line);
    lines.push({ end, line, start });
  });

  const pointPositions = new Float32Array(lines.length * 3);
  const pointGeometry = new THREE.BufferGeometry();
  pointGeometry.setAttribute("position", new THREE.BufferAttribute(pointPositions, 3));
  const points = new THREE.Points(
    pointGeometry,
    new THREE.PointsMaterial({
      color: 0xffffff,
      map: assets.dot,
      opacity: 0.66,
      size: 0.075,
      transparent: true,
      depthWrite: false,
      sizeAttenuation: true
    })
  );
  group.add(points);

  group.userData.tick = (time: number, _dt: number, _alpha: number, _progress: number, local: number) => {
    group.rotation.y = time * 0.07;
    const growth = clamp01(local * 1.18 - 0.1);
    nodes.forEach((node, index) => {
      const active = growth > index / nodes.length;
      const pulse = 1 + Math.sin(time * 1.4 + node.phase) * 0.08;
      node.mesh.scale.setScalar(active ? node.base * pulse : 0.001);
    });
    center.rotation.y = time * 0.4;
    lines.forEach((entry, index) => {
      const threshold = index / lines.length;
      const lineProgress = clamp01((growth - threshold) * 12);
      entry.line.geometry.setDrawRange(0, lineProgress > 0.2 ? 2 : 0);
      const material = entry.line.material as THREE.Material;
      material.opacity = 0.32 * lineProgress;
    });
    for (let i = 0; i < lines.length; i += 1) {
      const offset = ((time * 0.32 + i * 0.17) % 1);
      const entry = lines[i];
      const i3 = i * 3;
      pointPositions[i3] = lerp(entry.start.x, entry.end.x, offset);
      pointPositions[i3 + 1] = lerp(entry.start.y, entry.end.y, offset);
      pointPositions[i3 + 2] = lerp(entry.start.z, entry.end.z, offset);
    }
    pointGeometry.attributes.position.needsUpdate = true;
  };

  return group;
}

function makeDiscGlassTexture() {
  const size = 512;
  const canvas = makeCanvas(size, size);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  const gradient = context.createRadialGradient(
    size / 2,
    size / 2,
    0,
    size / 2,
    size / 2,
    size / 2
  );
  gradient.addColorStop(0, "rgba(255,255,255,0.16)");
  gradient.addColorStop(0.62, "rgba(255,255,255,0.06)");
  gradient.addColorStop(1, "rgba(255,255,255,0.2)");
  context.fillStyle = gradient;
  context.fillRect(0, 0, size, size);
  context.strokeStyle = "rgba(255,255,255,0.36)";
  context.lineWidth = 3;
  context.beginPath();
  context.arc(size / 2, size / 2, size / 2 - 6, 0, TAU);
  context.stroke();
  return tex(canvas);
}

function buildConnectorMatrix(assets: SceneAssets) {
  const group = new THREE.Group();
  group.rotation.x = 0.06;
  const disc = new THREE.Mesh(
    new THREE.CircleGeometry(3.15, 96),
    new THREE.MeshBasicMaterial({
      map: makeDiscGlassTexture(),
      opacity: 0.92,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide
    })
  );
  disc.position.z = -0.06;
  group.add(disc);

  const core = new THREE.LineSegments(sphereWire(0.27, 16, 10), lineMaterial(0.58));
  group.add(core);
  group.add(
    new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(circlePoints(0.52, 56, "xy")),
      lineMaterial(0.26)
    )
  );

  const sockets: Array<{
    delay: number;
    group: THREE.Group;
    material: THREE.Material;
    rayMaterial: THREE.Material;
  }> = [];
  const rings = [
    { count: 8, radius: 1.15, size: 0.085 },
    { count: 12, radius: 1.82, size: 0.1 },
    { count: 16, radius: 2.48, size: 0.115 }
  ];

  rings.forEach((ring, ringIndex) => {
    group.add(
      new THREE.Line(
        new THREE.BufferGeometry().setFromPoints(circlePoints(ring.radius, 128, "xy")),
        lineMaterial(0.18)
      )
    );
    for (let index = 0; index < ring.count; index += 1) {
      const angle = (index / ring.count) * TAU + ringIndex * 0.12;
      const x = Math.cos(angle) * ring.radius;
      const y = Math.sin(angle) * ring.radius;
      const socket = new THREE.Group();
      socket.position.set(x, y, 0.05);
      const material = lineMaterial(0.22);
      const shape =
        (index + ringIndex) % 2 === 0
          ? new THREE.LineLoop(
              new THREE.BufferGeometry().setFromPoints(circlePoints(ring.size, 24, "xy")),
              material
            )
          : new THREE.LineLoop(
              new THREE.BufferGeometry().setFromPoints(
                rectanglePoints(ring.size * 1.5, ring.size * 1.5)
              ),
              material
            );
      socket.add(shape);
      const rayMaterial = lineMaterial(0.1);
      const ray = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints([
          new THREE.Vector3(Math.cos(angle) * ring.size * 1.4, Math.sin(angle) * ring.size * 1.4, 0),
          new THREE.Vector3(Math.cos(angle) * 0.34, Math.sin(angle) * 0.34, 0)
        ]),
        rayMaterial
      );
      socket.add(ray);
      group.add(socket);
      sockets.push({
        delay: (ringIndex * 0.24 + index / ring.count * 0.62) / 1.1,
        group: socket,
        material,
        rayMaterial
      });
    }
  });

  group.userData.tick = (
    time: number,
    _dt: number,
    alpha: number,
    _progress: number,
    local: number
  ) => {
    core.rotation.set(time * 0.5, time * 0.6, 0);
    group.rotation.z = Math.sin(time * 0.18) * 0.02;
    sockets.forEach((socket) => {
      const active = local > socket.delay * 0.95;
      const pulse = active ? 0.66 + Math.sin(time * 3 + socket.delay * 18) * 0.18 : 0.16;
      socket.material.opacity = pulse * alpha;
      socket.rayMaterial.opacity = active ? 0.36 * alpha : 0.06 * alpha;
      socket.group.scale.setScalar(active ? 1.12 : 0.9);
    });
  };

  return group;
}

function makeKnowledgeLayerTexture(label: string) {
  const width = 768;
  const height = 180;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  context.clearRect(0, 0, width, height);
  context.fillStyle = "rgba(255,255,255,0.92)";
  context.font = `700 34px ${FONT_CN}`;
  context.fillText(label, 34, 72);
  context.fillStyle = "rgba(255,255,255,0.5)";
  context.font = `500 17px ${FONT_EN}`;
  setLetterSpacing(context, "5px");
  context.fillText("KNOWLEDGE LAYER", 36, 108);
  setLetterSpacing(context, "0px");
  for (let i = 0; i < 3; i += 1) {
    const barWidth = 280 - i * 42;
    context.fillStyle = `rgba(255,255,255,${0.4 - i * 0.08})`;
    context.fillRect(36, 130 + i * 13, barWidth, 3);
  }
  return tex(canvas);
}

function makeBookTexture(label: string, index: number) {
  const width = 560;
  const height = 760;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  context.clearRect(0, 0, width, height);
  roundRectPath(context, 5, 5, width - 10, height - 10, 26);
  const gradient = context.createLinearGradient(0, 0, width, height);
  gradient.addColorStop(0, "rgba(255,255,255,0.24)");
  gradient.addColorStop(0.5, "rgba(255,255,255,0.07)");
  gradient.addColorStop(1, "rgba(255,255,255,0.18)");
  context.fillStyle = gradient;
  context.fill();
  context.lineWidth = 2;
  context.strokeStyle = "rgba(255,255,255,0.62)";
  context.stroke();

  context.strokeStyle = "rgba(255,255,255,0.12)";
  context.lineWidth = 1;
  for (let x = 40; x < width - 30; x += 42) {
    context.beginPath();
    context.moveTo(x, 24);
    context.lineTo(x, height - 24);
    context.stroke();
  }

  context.fillStyle = "rgba(255,255,255,0.46)";
  context.font = `500 18px ${FONT_EN}`;
  setLetterSpacing(context, "4px");
  context.fillText(`VOLUME ${String(index + 1).padStart(2, "0")}`, 42, 78);
  setLetterSpacing(context, "0px");

  context.fillStyle = "rgba(255,255,255,0.94)";
  context.font = `700 62px ${FONT_CN}`;
  context.fillText(label, 40, 360);

  context.fillStyle = "rgba(255,255,255,0.5)";
  context.font = `500 18px ${FONT_EN}`;
  setLetterSpacing(context, "6px");
  context.fillText("PUDANS KNOWLEDGE", 42, 412);
  setLetterSpacing(context, "0px");

  for (let i = 0; i < 4; i += 1) {
    context.fillStyle = `rgba(255,255,255,${0.44 - i * 0.08})`;
    context.fillRect(42, 506 + i * 28, 310 - i * 36, 3);
  }

  context.strokeStyle = "rgba(255,255,255,0.22)";
  context.beginPath();
  context.moveTo(42, 646);
  context.lineTo(width - 42, 646);
  context.stroke();
  context.fillStyle = "rgba(255,255,255,0.44)";
  context.font = `500 16px ${FONT_EN}`;
  setLetterSpacing(context, "4px");
  context.fillText("ARCHIVE / OPEN", 42, 686);
  setLetterSpacing(context, "0px");
  return tex(canvas);
}

function makeBookPagesTexture() {
  const width = 560;
  const height = 760;
  const canvas = makeCanvas(width, height);
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas context unavailable");
  }
  context.clearRect(0, 0, width, height);
  roundRectPath(context, 8, 8, width - 16, height - 16, 22);
  context.fillStyle = "rgba(255,255,255,0.12)";
  context.fill();
  context.strokeStyle = "rgba(255,255,255,0.3)";
  context.lineWidth = 1.5;
  context.stroke();
  context.strokeStyle = "rgba(255,255,255,0.22)";
  for (let i = 0; i < 17; i += 1) {
    const y = 84 + i * 36;
    context.beginPath();
    context.moveTo(56, y);
    context.lineTo(width - 56 - (i % 4) * 22, y);
    context.stroke();
  }
  return tex(canvas);
}

function buildKnowledgeTower(assets: SceneAssets) {
  const group = new THREE.Group();
  const labels = ["白皮书", "指南", "规范", "手册", "案例", "方法论", "最佳实践"];
  const positions = [
    { x: -2.2, y: 1.4, z: -0.6 },
    { x: -0.8, y: 2.1, z: -1.2 },
    { x: 0.7, y: 1.7, z: 0.5 },
    { x: 2.1, y: 1.1, z: -0.8 },
    { x: -1.6, y: -1, z: 0.4 },
    { x: 0.2, y: -1.8, z: -0.6 },
    { x: 2.1, y: -1.1, z: 0.7 }
  ];
  const rotations = [-0.32, 0.18, -0.12, 0.28, -0.24, 0.14, -0.2];
  const pagesTexture = makeBookPagesTexture();
  const books: Array<{ base: THREE.Vector3; group: THREE.Group; phase: number; rotation: number }> = [];

  labels.forEach((label, index) => {
    const position = positions[index];
    const book = new THREE.Group();
    const pages = new THREE.Mesh(
      new THREE.PlaneGeometry(1.12, 1.55),
      new THREE.MeshBasicMaterial({
        map: pagesTexture,
        opacity: 0.78,
        transparent: true,
        depthWrite: false
      })
    );
    pages.position.z = -0.07;
    pages.rotation.y = 0.04;
    book.add(pages);

    const cover = new THREE.Mesh(
      new THREE.PlaneGeometry(1.16, 1.6),
      new THREE.MeshBasicMaterial({
        map: makeBookTexture(label, index),
        opacity: 0.95,
        transparent: true,
        depthWrite: false,
        side: THREE.DoubleSide
      })
    );
    book.add(cover);

    const spine = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(-0.54, -0.74, 0.04),
        new THREE.Vector3(-0.54, 0.74, 0.04)
      ]),
      lineMaterial(0.34)
    );
    book.add(spine);
    book.position.set(position.x, position.y - 0.7, position.z);
    book.rotation.set(0.08 + index * 0.01, rotations[index], Math.sin(index) * 0.08);
    group.add(book);
    books.push({
      base: new THREE.Vector3(position.x, position.y, position.z),
      group: book,
      phase: index * 0.8,
      rotation: rotations[index]
    });
  });

  group.userData.tick = (
    time: number,
    _dt: number,
    alpha: number,
    _progress: number,
    local: number
  ) => {
    books.forEach((book, index) => {
      const reveal = smoothstep(0, 1, clamp01(local * 1.6 - index * 0.06));
      book.group.position.set(
        book.base.x + Math.sin(time * 0.34 + book.phase) * 0.1,
        book.base.y - 0.7 + reveal * 0.7 + Math.sin(time * 0.55 + book.phase) * 0.12,
        book.base.z + Math.cos(time * 0.28 + book.phase) * 0.08
      );
      book.group.rotation.y = book.rotation + Math.sin(time * 0.32 + book.phase) * 0.12;
      book.group.rotation.z = Math.sin(index) * 0.08 + Math.sin(time * 0.4 + book.phase) * 0.025;
      book.group.scale.setScalar((0.78 + reveal * 0.22) * alpha);
    });
  };

  return group;
}

function createSpeedTrack(scene: THREE.Scene) {
  const group = new THREE.Group();
  scene.add(group);

  const crossMaterial = lineMaterial(0.22);
  const crossLines: THREE.Line[] = [];
  for (let i = 0; i < 26; i += 1) {
    const line = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(-46, 0, 0),
        new THREE.Vector3(46, 0, 0)
      ]),
      crossMaterial
    );
    line.position.z = -i * 2.6;
    group.add(line);
    crossLines.push(line);
  }

  [-7, 7].forEach((x) => {
    group.add(
      new THREE.Line(
        new THREE.BufferGeometry().setFromPoints([
          new THREE.Vector3(x, 0, 10),
          new THREE.Vector3(x, 0, -80)
        ]),
        lineMaterial(0.1)
      )
    );
  });

  const gateMaterial = lineMaterial(0.32);
  const gates: THREE.Group[] = [];
  for (let i = 0; i < 10; i += 1) {
    const gate = new THREE.Group();
    [-6, 6].forEach((x) => {
      gate.add(
        new THREE.Line(
          new THREE.BufferGeometry().setFromPoints([
            new THREE.Vector3(x, 0, 0),
            new THREE.Vector3(x, 2.7, 0)
          ]),
          gateMaterial
        )
      );
      gate.add(
        new THREE.Line(
          new THREE.BufferGeometry().setFromPoints([
            new THREE.Vector3(x * 1.02, 2.7, 0),
            new THREE.Vector3(x * 0.72, 2.7, 0)
          ]),
          gateMaterial
        )
      );
    });
    gate.position.z = -i * 7;
    group.add(gate);
    gates.push(gate);
  }

  const streakMaterial = lineMaterial(0.24);
  const streaks: Array<{ line: THREE.Line; speed: number }> = [];
  for (let i = 0; i < 16; i += 1) {
    const length = 2 + Math.random() * 6;
    const line = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(0, 0, 0),
        new THREE.Vector3(0, 0, -length)
      ]),
      streakMaterial
    );
    line.position.set(
      (Math.random() - 0.5) * 16,
      0.3 + Math.random() * 4.5,
      -2 - Math.random() * 40
    );
    group.add(line);
    streaks.push({ line, speed: 0.6 + Math.random() * 0.8 });
  }

  return {
    group,
    update(dt: number, cameraZ: number) {
      group.position.z = cameraZ;
      const speed = 26;
      crossLines.forEach((line) => {
        line.position.z += speed * dt;
        if (line.position.z > 5) {
          line.position.z -= 26 * 2.6;
        }
      });
      gates.forEach((gate) => {
        gate.position.z += speed * dt;
        if (gate.position.z > 6) {
          gate.position.z -= 70;
        }
      });
      streaks.forEach((streak) => {
        streak.line.position.z += speed * (1.4 + streak.speed) * dt;
        if (streak.line.position.z > 3) {
          streak.line.position.z -= 48;
          streak.line.position.x = (Math.random() - 0.5) * 16;
          streak.line.position.y = 0.3 + Math.random() * 4.5;
        }
      });
    }
  };
}

function createTrack(scene: THREE.Scene) {
  const group = new THREE.Group();
  const railMaterial = lineMaterial(0.13);
  const poleMaterial = lineMaterial(0.08);
  const z0 = 18;
  const z1 = -170;

  [-8.2, 8.2].forEach((x) => {
    const geometry = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(x, -4.6, z0),
      new THREE.Vector3(x, -4.6, z1)
    ]);
    group.add(new THREE.Line(geometry, railMaterial));
  });

  const points: THREE.Vector3[] = [];
  for (let z = 10; z > -170; z -= 9) {
    [-8.2, 8.2].forEach((x) => {
      points.push(new THREE.Vector3(x, -4.6, z), new THREE.Vector3(x, -1.5, z));
    });
  }
  group.add(new THREE.LineSegments(new THREE.BufferGeometry().setFromPoints(points), poleMaterial));
  group.add(
    new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(-34, -4.6, -170),
        new THREE.Vector3(34, -4.6, -170)
      ]),
      lineMaterial(0.05)
    )
  );
  scene.add(group);
  return group;
}

function createGlobe(radius: number, material: THREE.LineBasicMaterial, segments = 48) {
  const group = new THREE.Group();
  for (let i = 0; i < 12; i += 1) {
    const longitude = (i / 12) * Math.PI;
    const points: THREE.Vector3[] = [];
    for (let j = 0; j <= segments; j += 1) {
      const angle = (j / segments) * TAU;
      points.push(
        new THREE.Vector3(
          Math.cos(angle) * Math.sin(longitude) * radius,
          Math.sin(angle) * radius,
          Math.cos(angle) * Math.cos(longitude) * radius
        )
      );
    }
    group.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), material));
  }
  for (let i = 1; i < 9; i += 1) {
    const latitude = -Math.PI / 2 + (i / 9) * Math.PI;
    const ringRadius = Math.cos(latitude) * radius;
    const y = Math.sin(latitude) * radius;
    const points = circlePoints(ringRadius, segments, "xz").map(
      (point) => new THREE.Vector3(point.x, y, point.z)
    );
    group.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(points), material));
  }
  return group;
}

function wireGeometry(rings: Array<{ r: number; y: number }>, lonSeg = 12) {
  const positions: THREE.Vector3[] = [];
  const ringPoints = rings.map((ring) => {
    const points: THREE.Vector3[] = [];
    for (let i = 0; i < lonSeg; i += 1) {
      const angle = (i / lonSeg) * TAU;
      points.push(new THREE.Vector3(Math.cos(angle) * ring.r, ring.y, Math.sin(angle) * ring.r));
    }
    return points;
  });
  for (let ring = 0; ring < rings.length - 1; ring += 1) {
    for (let i = 0; i < lonSeg; i += 1) {
      positions.push(ringPoints[ring][i], ringPoints[ring + 1][i]);
    }
  }
  for (let ring = 0; ring < rings.length; ring += 1) {
    for (let i = 0; i < lonSeg; i += 1) {
      positions.push(ringPoints[ring][i], ringPoints[ring][(i + 1) % lonSeg]);
    }
  }
  return new THREE.BufferGeometry().setFromPoints(positions);
}

function sphereWire(radius: number, lon = 14, lat = 9) {
  const rings: Array<{ r: number; y: number }> = [];
  for (let i = 0; i <= lat; i += 1) {
    const theta = (i / lat) * Math.PI;
    rings.push({ r: Math.max(Math.sin(theta) * radius, 0.012), y: Math.cos(theta) * radius });
  }
  return wireGeometry(rings, lon);
}

function makeCylinderPart(
  length: number,
  topRadius: number,
  bottomRadius: number,
  lon: number,
  rings: number,
  material: THREE.LineBasicMaterial
) {
  const group = new THREE.Group();
  const ringDefs: Array<{ r: number; y: number }> = [];
  for (let i = 0; i <= rings; i += 1) {
    const ratio = i / rings;
    ringDefs.push({ r: topRadius + (bottomRadius - topRadius) * ratio, y: -length * ratio });
  }
  group.add(new THREE.LineSegments(wireGeometry(ringDefs, lon), material));
  return group;
}

function makeWireHead(
  radius: number,
  scaleX: number,
  scaleY: number,
  scaleZ: number,
  material: THREE.LineBasicMaterial
) {
  const group = new THREE.Group();
  const wire = new THREE.LineSegments(sphereWire(radius, 14, 9), material);
  wire.scale.set(scaleX, scaleY, scaleZ);
  group.add(wire);
  return group;
}

function makeWireBox(
  width: number,
  height: number,
  depth: number,
  material: THREE.LineBasicMaterial
) {
  const group = new THREE.Group();
  group.add(new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(width, height, depth)), material));
  return group;
}

function createWireRunner() {
  const root = new THREE.Group();
  const strong = lineMaterial(0.92);
  const soft = lineMaterial(0.42);
  const body = new THREE.Group();
  body.position.y = 1.12;
  root.add(body);

  const pelvis = makeCylinderPart(0.22, 0.2, 0.24, 14, 3, strong);
  pelvis.position.y = 0.12;
  body.add(pelvis);

  const torso = new THREE.Group();
  torso.position.y = 0.06;
  body.add(torso);
  const torsoLength = 0.64;
  const torsoWire = new THREE.LineSegments(
    wireGeometry(
      [
        { y: 0, r: 0.195 },
        { y: 0.14, r: 0.225 },
        { y: 0.36, r: 0.245 },
        { y: 0.54, r: 0.228 },
        { y: 0.64, r: 0.185 }
      ],
      18
    ),
    strong
  );
  torsoWire.scale.z = 0.68;
  torso.add(torsoWire);

  const chestDome = new THREE.LineSegments(sphereWire(0.238, 18, 6), strong);
  chestDome.scale.set(1.012, 0.41, 0.69);
  chestDome.position.y = torsoLength - 0.02;
  torso.add(chestDome);

  [-1, 1].forEach((side) => {
    const shoulder = new THREE.Group();
    const wire = new THREE.LineSegments(sphereWire(0.11, 10, 6), soft);
    wire.scale.set(1.03, 0.89, 0.81);
    shoulder.add(wire);
    shoulder.position.set(0.245 * side, torsoLength - 0.1, 0);
    torso.add(shoulder);
  });

  const neck = makeCylinderPart(0.13, 0.072, 0.085, 10, 2, strong);
  neck.position.y = torsoLength + 0.11;
  neck.rotation.x = Math.PI;
  torso.add(neck);

  const headGroup = new THREE.Group();
  headGroup.position.set(0, torsoLength + 0.3, -0.03);
  torso.add(headGroup);
  headGroup.add(makeWireHead(0.155, 0.86, 1.06, 0.95, strong));
  const jaw = makeWireBox(0.2, 0.11, 0.18, strong);
  jaw.position.set(0, -0.1, -0.05);
  headGroup.add(jaw);

  const arms: Array<{ shoulder: THREE.Group; elbow: THREE.Group; side: number }> = [];
  [-1, 1].forEach((side) => {
    const shoulder = new THREE.Group();
    shoulder.position.set(0.29 * side, torsoLength - 0.06, 0);
    torso.add(shoulder);
    shoulder.add(makeCylinderPart(0.31, 0.072, 0.06, 10, 3, strong));
    const elbow = new THREE.Group();
    elbow.position.y = -0.31;
    shoulder.add(elbow);
    elbow.add(makeCylinderPart(0.29, 0.058, 0.046, 10, 3, strong));
    const hand = new THREE.LineSegments(sphereWire(0.062, 8, 5), soft);
    hand.position.y = -0.3;
    hand.scale.set(1.1, 0.9, 1.1);
    elbow.add(hand);
    arms.push({ elbow, shoulder, side });
  });

  const legs: Array<{ ankle: THREE.Group; hip: THREE.Group; knee: THREE.Group }> = [];
  [-1, 1].forEach((side) => {
    const hip = new THREE.Group();
    hip.position.set(0.125 * side, -0.02, 0);
    body.add(hip);
    hip.add(makeCylinderPart(0.51, 0.115, 0.088, 12, 4, strong));
    const knee = new THREE.Group();
    knee.position.y = -0.51;
    hip.add(knee);
    knee.add(makeCylinderPart(0.49, 0.086, 0.056, 12, 4, strong));
    const ankle = new THREE.Group();
    ankle.position.y = -0.49;
    knee.add(ankle);
    const foot = makeWireBox(0.15, 0.085, 0.29, strong);
    foot.position.set(0, -0.035, -0.1);
    ankle.add(foot);
    legs.push({ ankle, hip, knee });
  });

  const update = (time: number) => {
    const phase = time * Math.PI * 2 * 1.95;
    legs.forEach((leg, index) => {
      const legPhase = phase + index * Math.PI;
      leg.hip.rotation.x = 0.78 * Math.sin(legPhase) - 0.04;
      leg.knee.rotation.x = -(0.3 + 1.15 * (0.5 - 0.5 * Math.cos(legPhase + 0.9)));
      leg.ankle.rotation.x = 0.18 + 0.34 * Math.sin(legPhase + 2.5);
    });
    arms.forEach((arm, index) => {
      const armPhase = phase + index * Math.PI;
      arm.shoulder.rotation.x = -0.7 * Math.sin(armPhase) - 0.12;
      arm.shoulder.rotation.z = arm.side * (0.12 + 0.05 * Math.sin(armPhase));
      arm.elbow.rotation.x = -(1.05 + 0.35 * Math.sin(armPhase + 0.45));
    });
    body.position.y = 1.1 + 0.09 * Math.cos(2 * phase);
    body.rotation.y = 0.16 * Math.sin(phase);
    body.rotation.z = 0.03 * Math.sin(phase);
    torso.rotation.x = -0.27 + 0.06 * Math.sin(2 * phase);
    torso.rotation.y = -0.19 * Math.sin(phase);
    headGroup.rotation.x = 0.1;
    headGroup.rotation.y = -0.1 * Math.sin(phase);
  };

  return { root, update };
}

function createCheetah() {
  const root = new THREE.Group();
  const strong = lineMaterial(0.9);
  const soft = lineMaterial(0.4);
  const body = new THREE.Group();
  body.position.y = 0.8;
  root.add(body);

  const trunk = new THREE.LineSegments(sphereWire(0.3, 16, 9), strong);
  trunk.scale.set(0.87, 0.81, 2.31);
  body.add(trunk);
  const belly = makeCylinderPart(0.34, 0.2, 0.17, 12, 3, soft);
  body.add(belly);

  const neck = new THREE.Group();
  neck.position.set(0, 0.1, -0.62);
  neck.rotation.x = -0.2;
  body.add(neck);
  neck.add(makeCylinderPart(0.3, 0.11, 0.13, 10, 3, strong));

  const head = new THREE.Group();
  head.position.set(0, 0, -0.46);
  neck.add(head);
  head.add(makeWireHead(0.15, 1, 0.92, 1.16, strong));
  const muzzle = makeWireBox(0.11, 0.09, 0.16, strong);
  muzzle.position.set(0, -0.05, -0.16);
  head.add(muzzle);
  [-1, 1].forEach((side) => {
    const ear = makeWireBox(0.06, 0.09, 0.03, strong);
    ear.position.set(0.07 * side, 0.14, 0.02);
    head.add(ear);
  });

  const legs: Array<{ hip: THREE.Group; knee: THREE.Group; paw: THREE.Group; ph0: number }> = [];
  [
    [0.17, -0.42, 0],
    [-0.17, -0.42, 0.3],
    [0.17, 0.46, Math.PI * 0.92],
    [-0.17, 0.46, Math.PI * 1.2]
  ].forEach(([x, z, ph0]) => {
    const hip = new THREE.Group();
    hip.position.set(x, -0.06, z);
    body.add(hip);
    hip.add(makeCylinderPart(0.32, 0.085, 0.062, 10, 3, strong));
    const knee = new THREE.Group();
    knee.position.y = -0.32;
    hip.add(knee);
    knee.add(makeCylinderPart(0.3, 0.062, 0.042, 10, 3, strong));
    const paw = new THREE.Group();
    paw.position.y = -0.3;
    knee.add(paw);
    const pawBox = makeWireBox(0.09, 0.05, 0.13, soft);
    pawBox.position.set(0, -0.02, -0.04);
    paw.add(pawBox);
    legs.push({ hip, knee, paw, ph0 });
  });

  const tail: Array<{ segment: THREE.Group; next: THREE.Group }> = [];
  let parent = new THREE.Group();
  parent.position.set(0, 0.1, 0.68);
  body.add(parent);
  for (let i = 0; i < 6; i += 1) {
    const segment = new THREE.Group();
    if (i === 0) {
      parent.add(segment);
    }
    segment.rotation.x = -0.16;
    const part = makeCylinderPart(0.17, 0.055 - i * 0.006, 0.045 - i * 0.006, 8, 2, strong);
    part.rotation.x = -Math.PI / 2;
    segment.add(part);
    const next = new THREE.Group();
    next.position.z = 0.17;
    segment.add(next);
    if (i > 0) {
      tail[tail.length - 1].next.add(segment);
    }
    tail.push({ next, segment });
    parent = next;
  }
  root.scale.setScalar(1.22);

  const update = (time: number) => {
    const phase = time * Math.PI * 2 * 1.95;
    legs.forEach((leg) => {
      const legPhase = phase + leg.ph0;
      leg.hip.rotation.x = 0.72 * Math.sin(legPhase) - 0.05;
      leg.knee.rotation.x = -(0.35 + 0.95 * (0.5 - 0.5 * Math.cos(legPhase + 0.9)));
      leg.paw.rotation.x = 0.25 * Math.sin(legPhase + 2);
    });
    body.position.y = 0.8 + 0.06 * Math.cos(2 * phase);
    body.rotation.x = 0.06 * Math.sin(2 * phase);
    body.rotation.y = 0.05 * Math.sin(phase);
    tail.forEach((entry, index) => {
      entry.segment.rotation.y = 0.16 * Math.sin(phase * 0.9 + index * 0.7);
      entry.segment.rotation.x = -0.16 + 0.1 * Math.sin(phase * 0.7 + index * 0.5);
    });
  };

  return { root, update };
}

function createRunner(scene: THREE.Scene, assets: SceneAssets, small: boolean) {
  const root = new THREE.Group();
  const globeMaterial = new THREE.LineBasicMaterial({
    color: 0xffffff,
    opacity: 0.6,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  const innerMaterial = new THREE.LineBasicMaterial({
    color: 0xffffff,
    opacity: 0.34,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  root.add(createGlobe(1.15, globeMaterial, 56), createGlobe(0.72, innerMaterial, 40));

  const core = new THREE.Mesh(
    new THREE.SphereGeometry(0.24, 16, 12),
    new THREE.MeshBasicMaterial({
      color: 0xffffff,
      opacity: 0.58,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
  );
  root.add(core);

  const glow = new THREE.Sprite(
    new THREE.SpriteMaterial({
      color: 0xffffff,
      map: assets.glow,
      opacity: 0.34,
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
  );
  glow.scale.set(6.8, 6.8, 1);
  root.add(glow);

  const ringMaterial = new THREE.LineBasicMaterial({
    color: 0xffffff,
    opacity: 0.3,
    transparent: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  const ringOne = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(circlePoints(1.75, 72, "xz")),
    ringMaterial
  );
  ringOne.rotation.set(1.15, 0, 0.4);
  const ringTwo = new THREE.Line(
    new THREE.BufferGeometry().setFromPoints(circlePoints(1.95, 72, "xz")),
    ringMaterial
  );
  ringTwo.rotation.set(-0.85, 0, -0.6);
  root.add(ringOne, ringTwo);

  const thrustMaterial = new THREE.LineBasicMaterial({
    color: 0xffffff,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    opacity: 0.22,
    transparent: true
  });
  for (let i = 0; i < 11; i += 1) {
    const length = 1.2 + Math.random() * 3.2;
    const line = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(0, 0, 0),
        new THREE.Vector3(0, 0, length)
      ]),
      thrustMaterial
    );
    line.position.set((Math.random() - 0.5) * 1.3, (Math.random() - 0.5) * 1.3, 0);
    root.add(line);
  }
  root.scale.setScalar(small ? 0.72 : 1.12);
  scene.add(root);

  const trailCount = small ? 58 : 110;
  const trailPositions = new Float32Array(trailCount * 3);
  const trailColors = new Float32Array(trailCount * 3);
  for (let i = 0; i < trailCount; i += 1) {
    const fade = 0.18 + 0.82 * Math.pow(1 - i / (trailCount - 1), 0.7);
    trailColors[i * 3] = fade;
    trailColors[i * 3 + 1] = fade;
    trailColors[i * 3 + 2] = fade;
  }
  const trailGeometry = new THREE.BufferGeometry();
  trailGeometry.setAttribute("position", new THREE.BufferAttribute(trailPositions, 3));
  trailGeometry.setAttribute("color", new THREE.BufferAttribute(trailColors, 3));
  const trailMaterial = new THREE.PointsMaterial({
    color: 0xffffff,
    map: assets.dot,
    opacity: 0.42,
    size: small ? 0.09 : 0.12,
    transparent: true,
    vertexColors: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false,
    sizeAttenuation: true
  });
  const trail = new THREE.Points(trailGeometry, trailMaterial);
  trail.frustumCulled = false;
  scene.add(trail);

  const materials = [
    globeMaterial,
    innerMaterial,
    ringMaterial,
    glow.material,
    core.material,
    thrustMaterial
  ];

  return {
    root,
    trail,
    update(time: number, cameraX: number, cameraY: number, cameraZ: number, alpha: number) {
      const follow = 9 + 6 * (0.5 - 0.5 * Math.cos(time * 0.55));
      const x = cameraX + (small ? 0.95 : 4.2) + Math.sin(time * 0.65) * (small ? 0.3 : 0.75);
      const y = cameraY + (small ? -0.55 : 0.15) + Math.sin(time * 1.25) * (small ? 0.35 : 0.55);
      const z = cameraZ - follow;
      root.position.set(x, y, z);
      root.rotation.set(Math.sin(time * 1.9) * 0.55, time * 5.2, time * 1.4);

      for (let i = 0; i < trailCount; i += 1) {
        const fraction = i / (trailCount - 1);
        const scale = small ? 0.62 : 1;
        const back = fraction * fraction * 2.2 * scale;
        const swirl = Math.sin(time * 4.2 - fraction * 6) * 0.5 * fraction * scale;
        const index = i * 3;
        trailPositions[index] = x + swirl + fraction * 0.25 * scale;
        trailPositions[index + 1] =
          y + Math.cos(time * 3.4 - fraction * 5) * 0.22 * fraction * scale - fraction * 0.42 * scale;
        trailPositions[index + 2] = z + back;
      }
      trailGeometry.attributes.position.needsUpdate = true;
      materials.forEach((material) => {
        material.opacity = (material.userData.base ?? material.opacity) * alpha;
      });
      trailMaterial.opacity = 0.42 * alpha;
    }
  };
}

function createStages(scene: THREE.Scene, assets: SceneAssets) {
  const builders: Record<string, () => THREE.Group> = {
    ai: () => buildAIMatrix(assets),
    connectors: () => buildConnectorMatrix(assets),
    data: () => buildDataCore(assets),
    graph: () => buildKnowledgeGraph(assets),
    hub: () => buildIntegrationHub(assets),
    library: () => buildKnowledgeTower(assets)
  };

  return STAGE_DEF.map((definition, index): StageRuntime => {
    const group = builders[definition.build]();
    group.position.set(definition.x, definition.y, definition.z);
    group.rotation.y = definition.ry;
    scene.add(group);
    return {
      group,
      home: new THREE.Vector3(definition.x, definition.y, definition.z),
      homeRy: definition.ry,
      local: 0,
      mats: collectFade(group),
      phase: index * 1.7,
      z: definition.z
    };
  });
}

function createFinale(scene: THREE.Scene, camera: THREE.PerspectiveCamera, root: HTMLElement, assets: SceneAssets, small: boolean) {
  const textElement = root.querySelector<HTMLElement>('[data-portal="finale-text"]');
  const container = root.querySelector<HTMLElement>('[data-portal="finale"]');
  let count = 0;
  let geometry: THREE.BufferGeometry | null = null;
  let points: THREE.Points | null = null;
  let material: THREE.PointsMaterial | null = null;
  let px: Float32Array | null = null;
  let py: Float32Array | null = null;
  let start: Float32Array | null = null;
  let target: Float32Array | null = null;
  let seeds: Float32Array | null = null;
  let sample: { width: number; height: number; points: number[]; minX: number; maxX: number; minY: number; maxY: number } | null = null;
  let measureContext: CanvasRenderingContext2D | null = null;

  const allocate = () => {
    if (!sample || !sample.points.length || !target || !start || !px || !py || !seeds) {
      return;
    }
    const positions = new Float32Array(count * 3);
    const colors = new Float32Array(count * 3);
    for (let i = 0; i < count; i += 1) {
      const sampleIndex = Math.floor((i * sample.points.length) / count);
      px[i] = sample.points[sampleIndex * 2];
      py[i] = sample.points[sampleIndex * 2 + 1];
      const i3 = i * 3;
      let sx: number;
      let sy: number;
      let sz: number;
      if (i % 5 < 2) {
        sx = FINALE_X + (Math.random() - 0.5) * 9.6;
        sy = FINALE_Y + (Math.random() - 0.5) * 6.2;
        sz = STAGE_DEF[5].z + (Math.random() - 0.5) * 0.8;
      } else {
        const angle = Math.random() * TAU;
        const radius = 6 + Math.sqrt(Math.random()) * 22;
        sx = FINALE_X + Math.cos(angle) * radius * 1.7;
        sy = FINALE_Y + Math.sin(angle) * radius * 0.95;
        sz = FINALE_Z + (Math.random() * 20 - 9);
      }
      start[i3] = sx;
      start[i3 + 1] = sy;
      start[i3 + 2] = sz;
      positions[i3] = sx;
      positions[i3 + 1] = sy;
      positions[i3 + 2] = sz;
      const value = 0.7 + Math.random() * 0.3;
      colors[i3] = value;
      colors[i3 + 1] = value;
      colors[i3 + 2] = value;
      seeds[i] = Math.random() * 0.34;
    }
    if (geometry) {
      geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
      geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
      geometry.setDrawRange(0, count);
    } else {
      geometry = new THREE.BufferGeometry();
      geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
      geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
      geometry.boundingSphere = new THREE.Sphere(
        new THREE.Vector3(FINALE_X, FINALE_Y, FINALE_Z),
        60
      );
      material = new THREE.PointsMaterial({
        color: 0xffffff,
        map: assets.dot,
        opacity: 0,
        size: 0.075,
        transparent: true,
        vertexColors: true,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        sizeAttenuation: true
      });
      points = new THREE.Points(geometry, material);
      points.frustumCulled = false;
      points.visible = false;
      scene.add(points);
    }
    layout();
  };

  const resample = () => {
    const width = 1400;
    const height = 460;
    const canvas = makeCanvas(width, height, true);
    const context = canvas.getContext("2d");
    if (!context) {
      return;
    }
    context.clearRect(0, 0, width, height);
    context.fillStyle = "#ffffff";
    context.textAlign = "center";
    context.textBaseline = "middle";
    const fontSize = 250;
    context.font = `700 ${fontSize}px ${FONT_EN}`;
    setLetterSpacing(context, `${fontSize * 0.04}px`);
    context.fillText("PUDANS", width / 2, height / 2);
    setLetterSpacing(context, "0px");

    const data = context.getImageData(0, 0, width, height).data;
    const sampled: number[] = [];
    let minX = width;
    let maxX = 0;
    let minY = height;
    let maxY = 0;
    const step = 2;
    for (let y = 0; y < height; y += step) {
      for (let x = 0; x < width; x += step) {
        if (data[(y * width + x) * 4 + 3] > 120) {
          sampled.push(x, y);
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    sample = { height, maxX, maxY, minX, minY, points: sampled, width };
    count = Math.min(small ? 7200 : 18000, Math.floor(sampled.length / 2));
    px = new Float32Array(count);
    py = new Float32Array(count);
    start = new Float32Array(count * 3);
    target = new Float32Array(count * 3);
    seeds = new Float32Array(count);
    allocate();
  };

  const layout = () => {
    if (!sample || !sample.points.length || !target || !textElement) {
      return;
    }
    if (!measureContext) {
      measureContext = makeCanvas(8, 8).getContext("2d");
    }
    if (!measureContext) {
      return;
    }
    const distance = Math.abs(END_Z - FINALE_Z);
    const visibleHeight = 2 * Math.tan(camera.fov * 0.5 * DEG) * distance;
    const visibleWidth = visibleHeight * camera.aspect;
    const probeSize = 100;
    measureContext.font = `700 ${probeSize}px ${FONT_EN}`;
    setLetterSpacing(measureContext, `${probeSize * 0.04}px`);
    const measuredWidth = measureContext.measureText("PUDANS").width || 380;
    let cssSize = (window.innerWidth * 0.7) / (measuredWidth / probeSize);
    cssSize = Math.min(cssSize, window.innerHeight * 0.32);
    cssSize = Math.max(cssSize, 34);
    textElement.style.fontSize = `${cssSize}px`;

    const rect = textElement.getBoundingClientRect();
    const domWidth = rect.width || cssSize * (measuredWidth / probeSize);
    const worldPerCss = visibleWidth / window.innerWidth;
    const worldWidth = domWidth * worldPerCss * 1.16;
    const shiftX = ((rect.left + rect.right) / 2 - window.innerWidth / 2) * worldPerCss;
    const shiftY = -((rect.top + rect.bottom) / 2 - window.innerHeight / 2) * worldPerCss;
    const boxWidth = Math.max(1, sample.maxX - sample.minX);
    const centerX = (sample.minX + sample.maxX) / 2;
    const centerY = (sample.minY + sample.maxY) / 2;
    const scale = worldWidth / boxWidth;

    for (let i = 0; i < count; i += 1) {
      target[i * 3] = FINALE_X + shiftX + ((px?.[i] ?? 0) - centerX) * scale;
      target[i * 3 + 1] = FINALE_Y + shiftY - ((py?.[i] ?? 0) - centerY) * scale;
      target[i * 3 + 2] = FINALE_Z;
    }
    if (geometry) {
      geometry.attributes.position.needsUpdate = true;
    }
    if (material) {
      material.size = Math.max(0.05, (3.2 * visibleHeight) / window.innerHeight);
    }
  };

  return {
    layout,
    resample,
    update(progress: number, time: number) {
      const visibility = smoothstep(0.895, 0.985, progress);
      if (!points || !material || visibility <= 0) {
        if (points) {
          points.visible = false;
        }
        if (container) {
          container.style.opacity = "0";
        }
        return;
      }
      points.visible = true;
      material.opacity = clamp01(visibility / 0.2);
      if (container) {
        container.style.opacity = String(smoothstep(0.93, 0.995, visibility));
      }
      if (!geometry || !target || !start || !seeds) {
        return;
      }
      const positions = geometry.attributes.position.array as Float32Array;
      for (let i = 0; i < count; i += 1) {
        let k = (visibility - seeds[i]) / (1 - seeds[i]);
        k = k <= 0 ? 0 : k >= 1 ? 1 : k;
        const eased = 1 - Math.pow(1 - k, 3);
        const i3 = i * 3;
        const remaining = 1 - eased;
        const swirl = remaining * 0.55;
        const angle = time * 1.6 + seeds[i] * 40;
        const axial = Math.sin(angle) * swirl;
        const vertical = Math.cos(angle * 1.3) * swirl;
        const jitterX = Math.sin(time * 2.1 + seeds[i] * 60) * 0.05 * eased;
        const jitterY = Math.cos(time * 2.4 + seeds[i] * 83) * 0.05 * eased;
        positions[i3] = start[i3] + (target[i3] - start[i3]) * eased + axial + jitterX;
        positions[i3 + 1] =
          start[i3 + 1] + (target[i3 + 1] - start[i3 + 1]) * eased + vertical + jitterY;
        positions[i3 + 2] = start[i3 + 2] + (target[i3 + 2] - start[i3 + 2]) * eased;
      }
      geometry.attributes.position.needsUpdate = true;
    },
    dispose() {
      if (textElement) {
        textElement.style.fontSize = "";
      }
      if (container) {
        container.style.opacity = "";
      }
    }
  };
}

export function createPortalScene({ canvas, progressRef, root }: PortalSceneOptions): PortalSceneHandle {
  const small = window.innerWidth < 820 || window.innerHeight < 560;
  const renderer = new THREE.WebGLRenderer({
    antialias: !small,
    alpha: true,
    canvas,
    powerPreference: "high-performance"
  });
  renderer.setClearColor(0x000000, 0);
  renderer.setClearAlpha(0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 400);
  camera.position.set(0, 0.8, START_Z);
  const clock = new THREE.Clock();
  const assets = createAssets();
  const state = {
    current: 0,
    mouseX: 0,
    mouseY: 0,
    targetMouseX: 0,
    targetMouseY: 0,
    time: 0
  };
  let frameId = 0;
  let loaderTimer = 0;
  let lastChapter = -1;

  const backgroundBlobs = {
    one: root.querySelector<HTMLElement>('[data-blob="1"]'),
    three: root.querySelector<HTMLElement>('[data-blob="3"]'),
    two: root.querySelector<HTMLElement>('[data-blob="2"]')
  };
  const hero = root.querySelector<HTMLElement>('[data-portal="hero"]');
  const rail = root.querySelector<HTMLElement>('[data-portal="rail"]');
  const railFill = root.querySelector<HTMLElement>('[data-portal="rail-fill"]');
  const railIndex = root.querySelector<HTMLElement>('[data-portal="rail-index"]');
  const caption = root.querySelector<HTMLElement>('[data-portal="caption"]');
  const captionIndex = root.querySelector<HTMLElement>('[data-portal="caption-index"]');
  const captionText = root.querySelector<HTMLElement>('[data-portal="caption-text"]');
  const topbar = root.querySelector<HTMLElement>('[data-portal="topbar"]');
  const hint = root.querySelector<HTMLElement>('[data-portal="hint"]');
  const loader = root.querySelector<HTMLElement>('[data-portal="loader"]');
  const meterNumber = root.querySelector<HTMLElement>('[data-portal="meter-number"]');
  const meterBar = root.querySelector<HTMLElement>('[data-portal="meter-bar"]');

  createTrack(scene);
  const speedTrack = createSpeedTrack(scene);
  const runner = createRunner(scene, assets, small);
  runner.root.traverse((object) => {
    if (object instanceof THREE.Mesh || object instanceof THREE.Line || object instanceof THREE.Sprite) {
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      materials.forEach((material) => {
        if (material.userData.base === undefined) {
          material.userData.base = material.opacity;
        }
      });
    }
  });
  const stages = createStages(scene, assets);
  const finale = createFinale(scene, camera, root, assets, small);

  const updateBackground = (progress: number, time: number) => {
    if (backgroundBlobs.one) {
      const scale = 1 + 0.1 * Math.sin(time * 0.22);
      backgroundBlobs.one.style.transform = `translate(-50%,-50%) translate3d(${-24 + 36 * progress}vmax, ${-20 + 26 * progress}vmax, 0) scale(${scale})`;
    }
    if (backgroundBlobs.two) {
      const scale = 1 + 0.12 * Math.sin(time * 0.33 + 1.2);
      backgroundBlobs.two.style.transform = `translate(-50%,-50%) translate3d(${30 + 8 * Math.sin(progress * 2.4 + 0.4)}vmax, ${26 + 6 * Math.sin(progress * 1.7)}vmax, 0) scale(${scale})`;
    }
    if (backgroundBlobs.three) {
      const scale = 1.05 + 0.08 * Math.cos(time * 0.27 + 0.6);
      backgroundBlobs.three.style.transform = `translate(-50%,-50%) translate3d(${-18 + 20 * progress}vmax, ${6 + 12 * progress}vmax, 0) scale(${scale})`;
    }
  };

  const updateUi = (progress: number) => {
    if (hero) {
      const alpha = 1 - smoothstep(0.01, 0.15, progress);
      hero.style.opacity = alpha.toFixed(3);
      hero.style.transform = `translate3d(0, ${(-progress * 70).toFixed(1)}px, 0)`;
    }
    if (railFill) {
      railFill.style.height = `${(progress * 100).toFixed(1)}%`;
    }
    if (rail) {
      rail.style.opacity = (1 - smoothstep(0.9, 0.96, progress)).toFixed(3);
    }
    let chapterIndex = 0;
    let best = Number.POSITIVE_INFINITY;
    CHAPTERS.forEach((chapter, index) => {
      const distance = Math.abs(progress - chapter.p);
      if (distance < best) {
        best = distance;
        chapterIndex = index;
      }
    });
    if (chapterIndex !== lastChapter) {
      lastChapter = chapterIndex;
      if (captionIndex) {
        captionIndex.textContent = CHAPTERS[chapterIndex].i;
      }
      if (captionText) {
        captionText.textContent = CHAPTERS[chapterIndex].t;
      }
      if (railIndex) {
        railIndex.textContent = CHAPTERS[chapterIndex].i;
      }
    }
    if (caption) {
      const alpha =
        (1 - smoothstep(0.1, 0.22, best)) *
        smoothstep(0.02, 0.1, progress) *
        (1 - smoothstep(0.9, 0.97, progress));
      caption.style.opacity = alpha.toFixed(3);
    }
    if (topbar) {
      topbar.style.opacity = (0.62 * (1 - smoothstep(0.8, 0.93, progress))).toFixed(3);
    }
    if (hint) {
      hint.style.opacity = (0.6 * (1 - smoothstep(0.01, 0.07, progress))).toFixed(3);
    }
    if (meterNumber) {
      meterNumber.textContent = String(Math.round(progress * 100)).padStart(3, "0");
    }
    if (meterBar) {
      meterBar.style.width = `${(progress * 100).toFixed(1)}%`;
    }
  };

  const updateStages = (progress: number, cameraZ: number, time: number, dt: number) => {
    const intro = smoothstep(0.02, 0.15, progress);
    stages.forEach((stage, index) => {
      const distance = cameraZ - stage.z;
      const nearFade =
        index === STAGE_COUNT - 1 ? smoothstep(0.8, 3.4, distance) : smoothstep(3, 7, distance);
      let alpha = nearFade * (1 - smoothstep(30, 42, distance)) * intro;
      if (index === STAGE_COUNT - 1) {
        alpha *= 1 - smoothstep(0.915, 0.972, progress);
      }
      const near = smoothstep(30, 12, distance);
      const lateralFactor = clamp(camera.aspect / 1.72, 0.34, 1);
      stage.group.position.set(
        stage.home.x * lateralFactor + Math.sin(time * 0.5 + stage.phase) * 0.1,
        stage.home.y + Math.sin(time * 0.62 + stage.phase) * 0.2 + near * 0.55,
        stage.home.z
      );
      stage.group.rotation.y = stage.homeRy + (1 - alpha) * 0.22 * (index % 2 ? -1 : 1);
      stage.group.rotation.x = Math.sin(time * 0.45 + stage.phase) * 0.012;
      stage.group.scale.setScalar((0.94 + alpha * 0.06) * (camera.aspect < 0.9 ? 0.68 : 1));
      stage.group.visible = alpha > 0.015;
      stage.mats.forEach((material) => {
        material.opacity = (material.userData.base ?? 0) * alpha;
      });
      const local = clamp01((cameraZ - (stage.z - 18)) / 18);
      stage.local = local;
      if (alpha > 0.12) {
        const tick = stage.group.userData.tick as
          | ((time: number, dt: number, alpha: number, progress: number, local: number) => void)
          | undefined;
        tick?.(time, dt, alpha, progress, local);
      }
    });
  };

  const update = (dt: number, time: number) => {
    const target = progressRef.current?.target ?? 0;
    const damp = 1 - Math.pow(0.92, dt * 60);
    state.current = lerp(state.current, target, damp);
    if (progressRef.current) {
      progressRef.current.current = state.current;
    }
    const progress = state.current;
    state.mouseX = lerp(state.mouseX, state.targetMouseX, damp);
    state.mouseY = lerp(state.mouseY, state.targetMouseY, damp);

    const lateralFactor = clamp(camera.aspect / 1.72, 0.34, 1);
    const cameraX =
      Math.sin(progress * Math.PI * 3.1) * 0.42 + state.mouseX * 0.55 * lateralFactor;
    const cameraY = 1.95 + Math.sin(progress * Math.PI * 4.4) * 0.2 - state.mouseY * 0.22;
    const cameraZ = lerp(START_Z, END_Z, progress);
    camera.position.set(cameraX, cameraY, cameraZ);
    const lookX = sampleLook(LOOK_X, progress) * lateralFactor + state.mouseX * 0.55 * lateralFactor;
    const lookY = sampleLook(LOOK_Y, progress) - state.mouseY * 0.2;
    camera.lookAt(lookX, lookY, cameraZ - 14);
    camera.rotateZ(Math.sin(progress * Math.PI * 2.4) * 0.035);

    const runnerAlpha = 1 - smoothstep(0.82, 0.93, progress);
    runner.update(time, cameraX, cameraY, cameraZ, runnerAlpha);
    runner.root.visible = runnerAlpha > 0.01;
    runner.trail.visible = runner.root.visible;

    speedTrack.update(dt, cameraZ);
    updateStages(progress, cameraZ, time, dt);
    finale.update(progress, time);
    updateBackground(progress, time);
    updateUi(progress);
  };

  const onResize = () => {
    const width = window.innerWidth;
    const height = window.innerHeight;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, small ? 1.6 : 2));
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.fov = width / height < 0.85 ? 58 : 50;
    camera.updateProjectionMatrix();
    finale.layout();
  };

  const animate = () => {
    frameId = requestAnimationFrame(animate);
    const dt = Math.min(clock.getDelta(), 0.05);
    state.time += dt;
    update(dt, state.time);
    renderer.render(scene, camera);
  };

  const onPointerMove = (event: PointerEvent) => {
    state.targetMouseX = (event.clientX / window.innerWidth - 0.5) * 2;
    state.targetMouseY = (event.clientY / window.innerHeight - 0.5) * 2;
  };

  return {
    start() {
      onResize();
      window.addEventListener("resize", onResize);
      window.addEventListener("orientationchange", onResize);
      window.addEventListener("pointermove", onPointerMove, { passive: true });
      finale.resample();
      finale.layout();
      document.fonts?.ready.then(() => {
        finale.resample();
        finale.layout();
      }).catch(() => undefined);
      loaderTimer = window.setTimeout(() => {
        if (loader) {
          loader.style.opacity = "0";
        }
      }, 380);
      const maxScroll = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
      const initial = clamp01((window.scrollY || 0) / maxScroll);
      state.current = initial;
      if (progressRef.current) {
        progressRef.current.target = initial;
        progressRef.current.current = initial;
      }
      animate();
    },
    dispose() {
      cancelAnimationFrame(frameId);
      window.clearTimeout(loaderTimer);
      window.removeEventListener("resize", onResize);
      window.removeEventListener("orientationchange", onResize);
      window.removeEventListener("pointermove", onPointerMove);
      scene.traverse((object) => {
        if (object instanceof THREE.Mesh || object instanceof THREE.Line || object instanceof THREE.Points) {
          object.geometry.dispose();
          const materials = Array.isArray(object.material) ? object.material : [object.material];
          materials.forEach((material) => {
            Object.values(material as unknown as Record<string, unknown>).forEach((value) => {
              if (value instanceof THREE.Texture) {
                value.dispose();
              }
            });
            material.dispose();
          });
        }
      });
      Object.values(assets).forEach((value) => {
        if (value instanceof THREE.Texture) {
          value.dispose();
        }
      });
      assets.cards.forEach((texture) => texture.dispose());
      finale.dispose();
      renderer.dispose();
      if (backgroundBlobs.one) {
        backgroundBlobs.one.style.transform = "";
      }
      if (backgroundBlobs.two) {
        backgroundBlobs.two.style.transform = "";
      }
      if (backgroundBlobs.three) {
        backgroundBlobs.three.style.transform = "";
      }
    }
  };
}