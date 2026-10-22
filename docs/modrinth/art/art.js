// Drawing helpers for the Modrinth page art. Same visual language as OnePixel's
// itch art and Last Pixel's banner: a dot-matrix field, chunky pixel type with
// hard drop shadows, scanlined titles, pixel bursts and a hand-drawn coral note.
// Plain script (not a module) so the pages render straight from file://.

const PAL = {
  bg: '#171717',
  ink: '#d4d4d4',
  white: '#f2f2f2',
  light: '#bdbdbd',
  mid: '#7a7a7a',
  dark: '#3a3a3a',
  slot: '#222222',
  gold: '#ffcc33',
  red: '#ff4d3d',
  cyan: '#5ee6ff',
  orange: '#ff9a2e',
  green: '#5dff6a',
  blue: '#4d7cff',
  violet: '#b45dff',
  peach: '#f7a48d',
  peachDark: '#a8604f',
  coral: '#ff6f61',
}

const FONT = { pixel: '"Press Start 2P"', term: 'VT323', hand: 'Caveat' }

class Rng {
  constructor(seed) { this.s = seed >>> 0 || 1 }
  next() { this.s ^= this.s << 13; this.s ^= this.s >>> 17; this.s ^= this.s << 5; return (this.s >>> 0) / 4294967296 }
  range(a, b) { return a + (b - a) * this.next() }
  int(a, b) { return Math.floor(this.range(a, b + 1)) }
  pick(list) { return list[this.int(0, list.length - 1)] }
}

async function setup() {
  await Promise.all([
    document.fonts.load(`16px ${FONT.pixel}`),
    document.fonts.load(`16px ${FONT.term}`),
    document.fonts.load(`600 16px ${FONT.hand}`),
  ])
  // Headless Chrome's viewport is shorter than its window, so render.sh passes
  // the intended size in the query string.
  const q = new URLSearchParams(location.search)
  const w = Number(q.get('w')) || window.innerWidth
  const h = Number(q.get('h')) || window.innerHeight
  const dpr = window.devicePixelRatio || 1
  const canvas = document.querySelector('canvas')
  canvas.width = Math.round(w * dpr)
  canvas.height = Math.round(h * dpr)
  canvas.style.width = `${w}px`
  canvas.style.height = `${h}px`
  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.imageSmoothingEnabled = false
  ctx.fillStyle = PAL.bg
  ctx.fillRect(0, 0, w, h)
  return { ctx, w, h, rng: new Rng(7) }
}

/** OnePixel's LED dot-matrix backdrop: faint dots with the odd brighter one. */
function field(ctx, w, h, seed = 1337, spacing = 10) {
  const rng = new Rng(seed)
  for (let y = spacing / 2; y < h; y += spacing) {
    for (let x = spacing / 2; x < w; x += spacing) {
      const roll = rng.next()
      const a = roll < 0.08 ? 0.2 + rng.next() * 0.22 : 0.06 + rng.next() * 0.08
      ctx.fillStyle = `rgba(122,122,122,${a})`
      ctx.fillRect(Math.round(x - 1), Math.round(y - 1), 2, 2)
    }
  }
}

/** Fades the field towards the background colour over a rectangle, left to right. */
function fade(ctx, x, y, w, h, from = 0.92, to = 0) {
  const g = ctx.createLinearGradient(x, 0, x + w, 0)
  g.addColorStop(0, `rgba(23,23,23,${from})`)
  g.addColorStop(0.75, `rgba(23,23,23,${(from + to) / 2})`)
  g.addColorStop(1, `rgba(23,23,23,${to})`)
  ctx.fillStyle = g
  ctx.fillRect(x, y, w, h)
}

function vignette(ctx, w, h, strength = 0.55) {
  const g = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.3, w / 2, h / 2, Math.max(w, h) * 0.75)
  g.addColorStop(0, 'rgba(10,10,10,0)')
  g.addColorStop(1, `rgba(10,10,10,${strength})`)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, w, h)
}

/** A pixel as OnePixel draws it: a solid square in a faint halo of its own colour. */
function pixel(ctx, x, y, s, color = PAL.white, halo = 0.07) {
  const g = Math.round(s * 2.1)
  ctx.fillStyle = color
  ctx.globalAlpha = halo
  ctx.fillRect(Math.round(x - g / 2), Math.round(y - g / 2), g, g)
  ctx.globalAlpha = 1
  ctx.fillRect(Math.round(x - s / 2), Math.round(y - s / 2), s, s)
}

/** OnePixel's target bracket. */
function bracket(ctx, x, y, gap, arm, t = 2, color = PAL.light) {
  ctx.fillStyle = color
  const l = Math.round(x - gap), r = Math.round(x + gap), top = Math.round(y - gap), b = Math.round(y + gap)
  for (const [px, py, sx, sy] of [[l, top, 1, 1], [r, top, -1, 1], [l, b, 1, -1], [r, b, -1, -1]]) {
    ctx.fillRect(sx > 0 ? px : px - arm, sy > 0 ? py : py - t, arm, t)
    ctx.fillRect(sx > 0 ? px : px - t, sy > 0 ? py : py - arm, t, arm)
  }
}

function burst(ctx, rng, x, y, r, n, colors, chip = 4) {
  for (let i = 0; i < n; i++) {
    const a = rng.range(0, Math.PI * 2)
    const d = r * Math.sqrt(rng.next())
    const s = Math.max(2, Math.round(chip * rng.range(0.5, 1.3)))
    ctx.fillStyle = colors[rng.int(0, colors.length - 1)]
    ctx.globalAlpha = rng.range(0.6, 1)
    ctx.fillRect(Math.round(x + Math.cos(a) * d - s / 2), Math.round(y + Math.sin(a) * d - s / 2), s, s)
  }
  ctx.globalAlpha = 1
}

function ring(ctx, x, y, r, color, alpha = 0.6, dot = 2) {
  ctx.fillStyle = color
  ctx.globalAlpha = alpha
  const steps = Math.round((Math.PI * 2 * r) / (dot * 3))
  for (let i = 0; i < steps; i++) {
    const a = (i / steps) * Math.PI * 2
    ctx.fillRect(Math.round(x + Math.cos(a) * r - dot / 2), Math.round(y + Math.sin(a) * r - dot / 2), dot, dot)
  }
  ctx.globalAlpha = 1
}

/** A firework: radial streaks of chips plus a dotted ring. */
function firework(ctx, rng, x, y, r, colors) {
  for (let i = 0; i < 18; i++) {
    const a = (i / 18) * Math.PI * 2 + rng.range(-0.08, 0.08)
    const len = r * rng.range(0.75, 1)
    for (let d = r * 0.25; d < len; d += 6) {
      ctx.fillStyle = colors[i % colors.length]
      ctx.globalAlpha = 1 - d / len * 0.7
      const s = d > len - 8 ? 4 : 3
      ctx.fillRect(Math.round(x + Math.cos(a) * d - s / 2), Math.round(y + Math.sin(a) * d - s / 2), s, s)
    }
  }
  ctx.globalAlpha = 1
  ring(ctx, x, y, r * 1.1, colors[0], 0.45)
}

/** Text with a hard 1-bit drop shadow, like OnePixel's floating numbers. */
function text(ctx, str, x, y, { font, size, color, align = 'center', shadow = PAL.bg, offset = Math.max(1, Math.round(size / 12)), weight = '', spacing = 0 }) {
  ctx.font = `${weight} ${size}px ${font}`.trim()
  ctx.textAlign = align
  ctx.textBaseline = 'middle'
  ctx.letterSpacing = `${spacing}px`
  if (shadow) {
    ctx.fillStyle = shadow
    ctx.fillText(str, x + offset, y + offset)
  }
  ctx.fillStyle = color
  ctx.fillText(str, x, y)
  ctx.letterSpacing = '0px'
  return ctx.measureText(str).width
}

/**
 * Last Pixel's title treatment: chunky pixel letters cut into scanlines with a
 * dark offset shadow. `parts` is a list of [text, fill] drawn left to right;
 * fill may be a colour or a [top, bottom] gradient pair.
 */
function scanTitle(ctx, parts, x, y, size, { align = 'left', spacing = 4, shadow = '#0b0b0b' } = {}) {
  ctx.font = `${size}px ${FONT.pixel}`
  ctx.letterSpacing = `${spacing}px`
  const widths = parts.map(([s]) => ctx.measureText(s).width)
  const total = widths.reduce((a, b) => a + b, 0)
  let cx = align === 'center' ? x - total / 2 : align === 'right' ? x - total : x
  const off = document.createElement('canvas')
  const dpr = window.devicePixelRatio || 1
  off.width = Math.ceil((total + size) * dpr)
  off.height = Math.ceil(size * 1.6 * dpr)
  const o = off.getContext('2d')
  o.setTransform(dpr, 0, 0, dpr, 0, 0)
  o.font = ctx.font
  o.letterSpacing = ctx.letterSpacing
  o.textBaseline = 'middle'
  let ox = 0
  parts.forEach(([s, fill], i) => {
    if (Array.isArray(fill)) {
      const g = o.createLinearGradient(0, size * 0.3, 0, size * 1.3)
      g.addColorStop(0, fill[0])
      g.addColorStop(1, fill[1])
      o.fillStyle = g
    } else {
      o.fillStyle = fill
    }
    o.fillText(s, ox, size * 0.8)
    ox += widths[i]
  })
  // Cut scanlines: every third device row loses most of its brightness.
  o.setTransform(1, 0, 0, 1, 0, 0)
  o.globalCompositeOperation = 'destination-out'
  const band = Math.max(1, Math.round(size * dpr / 22))
  for (let row = 0; row < off.height; row += band * 3) {
    o.globalAlpha = 0.55
    o.fillRect(0, row + band * 2, off.width, band)
  }
  // Shadow, then the striped letters.
  ctx.letterSpacing = `${spacing}px`
  ctx.textBaseline = 'middle'
  ctx.fillStyle = shadow
  ctx.textAlign = 'left'
  ctx.fillText(parts.map(([s]) => s).join(''), cx + size / 9, y + size / 9)
  ctx.drawImage(off, cx, y - size * 0.8, off.width / dpr, off.height / dpr)
  ctx.letterSpacing = '0px'
  return { left: cx, width: total }
}

/** OnePixel's hand-drawn coral arrow and note, tip at (x, y). */
function handNote(ctx, x, y, label, { scale = 1, flip = false, rotate = -7 } = {}) {
  ctx.save()
  ctx.translate(x, y)
  if (flip) ctx.scale(-1, 1)
  ctx.scale(scale, scale)
  ctx.translate(-6, -14)
  ctx.strokeStyle = PAL.coral
  ctx.lineWidth = 2.5
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.stroke(new Path2D('M52 45 C 38 53, 14 42, 6 14'))
  ctx.stroke(new Path2D('M5 25 L6 14 L15 19'))
  ctx.translate(58, 40)
  if (flip) ctx.scale(-1, 1)
  ctx.rotate((rotate * Math.PI) / 180)
  ctx.font = `600 30px ${FONT.hand}`
  ctx.textAlign = flip ? 'right' : 'left'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = PAL.coral
  ctx.fillText(label, 0, 0)
  ctx.restore()
}

// ------------------------------------------------------------- block tiles

/** 16x16 pixel textures, generated so the page ships no game assets. */
const BLOCKS = {
  grass(r, x, y) {
    const edge = 3 + ((x * 7) % 3 === 0 ? 1 : 0) + (x % 5 === 0 ? 1 : 0)
    if (y < edge) return r.pick(['#5a9e3a', '#6cbf45', '#4c8a30', '#62b03f'])
    return r.next() < 0.12 ? r.pick(['#6b4423', '#a87a4a']) : r.pick(['#8b5a2b', '#7a4f26', '#946235'])
  },
  dirt: (r) => (r.next() < 0.14 ? r.pick(['#6b4423', '#a87a4a']) : r.pick(['#8b5a2b', '#7a4f26', '#946235'])),
  stone: (r) => r.pick(['#8a8a8a', '#7d7d7d', '#959595', '#858585', r.next() < 0.3 ? '#6e6e6e' : '#8a8a8a']),
  cobble(r, x, y) {
    const cell = ((x >> 2) * 3 + (y >> 2) * 5 + ((x + y) >> 3)) % 4
    if (x % 4 === 0 || y % 4 === 0) return r.next() < 0.7 ? '#5a5a5a' : '#6a6a6a'
    return ['#9a9a9a', '#8a8a8a', '#a5a5a5', '#7e7e7e'][cell]
  },
  diamondOre(r, x, y) {
    const spots = [[3, 3], [4, 3], [3, 4], [10, 5], [11, 5], [11, 6], [6, 10], [7, 10], [7, 11], [12, 12], [12, 11]]
    if (spots.some(([a, b]) => a === x && b === y)) return r.pick(['#5ee6ff', '#a6f4ff', '#2fc4d9'])
    return BLOCKS.stone(r)
  },
  gold(r, x, y) {
    if (x === 0 || y === 0) return '#fff2a8'
    if (x === 15 || y === 15) return '#c98f00'
    return r.pick(['#ffcc33', '#ffd84d', '#f2b90f'])
  },
  log(r, x) {
    return x % 4 === 0 ? '#3d2a14' : r.pick(['#6b4a24', '#5c3f1e', '#755228'])
  },
  planks(r, x, y) {
    if (y % 4 === 3) return '#6e4f2a'
    if ((y >> 2) % 2 === 0 ? x === 5 : x === 12) return '#6e4f2a'
    return r.pick(['#b8945a', '#a8854e', '#c29d62'])
  },
  bricks(r, x, y) {
    if (y % 4 === 3) return '#bdb3a5'
    if ((y >> 2) % 2 === 0 ? x % 8 === 7 : x % 8 === 3) return '#bdb3a5'
    return r.pick(['#9c4a3a', '#8a3f31', '#a85444'])
  },
  sand: (r) => r.pick(['#e6d59a', '#dbca8c', '#efe0a8', '#d8c47f']),
  netherrack: (r) => r.pick(['#7a2a2a', '#8f3333', '#6a2222', '#a03c3c']),
  crying(r) {
    return r.next() < 0.16 ? r.pick(['#b45dff', '#d18cff']) : r.pick(['#1a0f2e', '#24153d', '#120a22'])
  },
  amethyst: (r, x, y) => ((x + y) % 5 === 0 ? '#e0b3ff' : r.pick(['#8a5bc4', '#9e6fd6', '#7647b0'])),
  copper(r, x, y) {
    if ((x + y * 3) % 11 === 0) return '#5fc4a0'
    return r.pick(['#c06b4a', '#d47f5a', '#b05e40'])
  },
  sculk: (r) => (r.next() < 0.1 ? '#2ee6d8' : r.pick(['#0b2b33', '#0f3a44', '#082026'])),
  ice: (r, x, y) => ((x - y + 16) % 6 === 0 ? '#e8f6ff' : r.pick(['#8fc2f2', '#a3cff5', '#7fb4e8'])),
  tnt(r, x, y) {
    if (y >= 6 && y <= 9) return x % 2 === 0 ? '#f2f2f2' : '#dcdcdc'
    return r.pick(['#d33a2c', '#c2312a', '#e04536'])
  },
  beacon(r, x, y) {
    if (x === 0 || y === 0 || x === 15 || y === 15) return '#d8f8ff'
    if (x > 4 && x < 11 && y > 4 && y < 11) return r.pick(['#5ee6ff', '#a6f4ff'])
    return r.pick(['#3a7f8f', '#2d6673'])
  },
  gilded(r) {
    return r.next() < 0.14 ? r.pick(['#ffcc33', '#e0a800']) : r.pick(['#2a2428', '#342c31', '#221d20'])
  },
  netherite: (r, x, y) => ((x * y) % 13 === 1 ? '#6b5f63' : r.pick(['#3d3538', '#463d41', '#332c2f'])),
  melon: (r, x) => (x % 3 === 0 ? '#8cbf3a' : r.pick(['#4f8a2a', '#5c9b30'])),
  wool: (r) => r.pick(['#f2f2f2', '#e6e6e6', '#ffffff']),
  peachWool: (r) => r.pick([PAL.peach, '#ffb7a2', '#eb9580']),
  glass(r, x, y) {
    if (x === 0 || y === 0 || x === 15 || y === 15) return '#d8f0f5'
    if ((x === 3 && y < 6 && y > 1) || (x === 4 && y === 2)) return '#ffffff'
    return null
  },
  bookshelf(r, x, y) {
    if (y < 2 || y > 13 || y === 7 || y === 8) return r.pick(['#b8945a', '#a8854e'])
    return ['#7a2a2a', '#2a4f7a', '#3a7a2a', '#7a6a2a'][(x >> 2) % 4]
  },
}

function grey(hex, dim = 0.62) {
  const n = parseInt(hex.slice(1), 16)
  const l = (0.3 * (n >> 16) + 0.59 * ((n >> 8) & 255) + 0.11 * (n & 255)) * dim
  const v = Math.round(l).toString(16).padStart(2, '0')
  return `#${v}${v}${v}`
}

/**
 * A collection slot: dark GUI cell with the block's texture inside. Missing
 * blocks are drawn in greyscale, the Last Pixel "world gone grey" look.
 */
function tile(ctx, kind, x, y, size, { found = true, seed = 3, check = false, glow = null } = {}) {
  const r = new Rng(seed * 97 + kind.length * 13)
  const pad = Math.round(size * 0.1)
  ctx.fillStyle = '#0f0f0f'
  ctx.fillRect(x, y, size, size)
  ctx.fillStyle = found ? '#2a2a2a' : PAL.slot
  ctx.fillRect(x + 2, y + 2, size - 4, size - 4)
  if (glow) {
    ctx.fillStyle = glow
    ctx.globalAlpha = 0.18
    ctx.fillRect(x - size * 0.18, y - size * 0.18, size * 1.36, size * 1.36)
    ctx.globalAlpha = 1
  }
  const inner = size - pad * 2
  const cell = inner / 16
  for (let py = 0; py < 16; py++) {
    for (let px = 0; px < 16; px++) {
      const c = BLOCKS[kind](r, px, py)
      if (!c) continue
      ctx.fillStyle = found ? c : grey(c)
      ctx.fillRect(Math.floor(x + pad + px * cell), Math.floor(y + pad + py * cell), Math.ceil(cell), Math.ceil(cell))
    }
  }
  if (check) {
    const s = Math.max(2, Math.round(size / 18))
    const bx = x + size - pad - s * 6
    const by = y + size - pad - s * 4
    ctx.fillStyle = '#0f0f0f'
    for (const [a, b] of [[0, 2], [1, 3], [2, 2], [3, 1], [4, 0]]) ctx.fillRect(bx + a * s - s / 2, by + b * s - s / 2, s * 2, s * 2)
    ctx.fillStyle = PAL.green
    for (const [a, b] of [[0, 2], [1, 3], [2, 2], [3, 1], [4, 0]]) ctx.fillRect(bx + a * s, by + b * s, s, s)
  }
}

/** Last Pixel's loading bar: a framed row of segments, `filled` of `count` lit. */
function segmentBar(ctx, x, y, w, h, count, filled, { on = PAL.light, hot = PAL.peach, hotCount = 3 } = {}) {
  ctx.strokeStyle = '#4a4a4a'
  ctx.lineWidth = 2
  ctx.strokeRect(x, y, w, h)
  const gap = 4
  const seg = (w - gap * (count + 1)) / count
  for (let i = 0; i < count; i++) {
    const lit = i < filled
    ctx.fillStyle = !lit ? '#262626' : i >= filled - hotCount ? hot : on
    ctx.fillRect(Math.round(x + gap + i * (seg + gap)), y + gap, Math.floor(seg), h - gap * 2)
  }
}

function done() {
  window.artReady = true
}
