/* Oriel UI optional ecosystem bridges. No network, prototype patches or background work on import. */
(function (global) {
  'use strict';
  if (global.OrielShims) return;

  function cross(a, b, c) {
    return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
  }

  function triangle(ctx, a, b, c) {
    ctx.fillQuad(a[0], a[1], b[0], b[1], c[0], c[1], c[0], c[1]);
  }

  // A four-point Canvas nonzero fill, using at most two native triangles.
  // Confetti's rounded vertices can be concave or cross even in flat mode.
  function fillFour(ctx, points) {
    for (var edge = 0; edge < 2; edge++) {
      var a = points[edge], b = points[edge + 1];
      var c = points[edge + 2], d = points[(edge + 3) % 4];
      var abC = cross(a, b, c), abD = cross(a, b, d);
      var cdA = cross(c, d, a), cdB = cross(c, d, b);
      if (abC * abD < 0 && cdA * cdB < 0) {
        var t = cdA / (cdA - cdB);
        var intersection = [a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])];
        triangle(ctx, intersection, b, c);
        triangle(ctx, intersection, d, a);
        return;
      }
    }
    var area = 0;
    for (var i = 0; i < 4; i++) {
      var next = points[(i + 1) % 4];
      area += points[i][0] * next[1] - next[0] * points[i][1];
    }
    if (area === 0) return; // Rounded, retraced edges have no filled area.
    for (var j = 0; j < 4; j++) {
      if (cross(points[(j + 3) % 4], points[j], points[(j + 1) % 4]) * area < 0) {
        triangle(ctx, points[j], points[(j + 1) % 4], points[(j + 2) % 4]);
        triangle(ctx, points[j], points[(j + 2) % 4], points[(j + 3) % 4]);
        return;
      }
    }
    ctx.fillQuad(points[0][0], points[0][1], points[1][0], points[1][1],
      points[2][0], points[2][1], points[3][0], points[3][1]);
  }

  function quadContext(canvas) {
    var ctx = canvas.getContext('2d');
    if (!ctx || typeof ctx.fillQuad !== 'function') {
      throw new Error('OrielShims.quadContext requires an Oriel Canvas with fillQuad; update the runtime');
    }
    if (ctx.__orielFourPointPath) return ctx;
    var points = [], started = false;
    function point(x, y) {
      if (!started || points.length >= 4 || typeof x !== 'number' || typeof y !== 'number' ||
          !isFinite(x) || !isFinite(y)) {
        throw new Error('OrielShims: only a finite four-point path is supported (docs/zh/ecosystem.md)');
      }
      points.push([x, y]);
    }
    ctx.beginPath = function () { points = []; started = true; };
    ctx.moveTo = function (x, y) {
      if (points.length) throw new Error('OrielShims: multiple subpaths are unsupported');
      point(x, y);
    };
    ctx.lineTo = point;
    ctx.closePath = function () {};
    ctx.fill = function () {
      if (arguments.length || points.length !== 4) {
        throw new Error('OrielShims: fill requires exactly four points and no Path2D/fill rule argument');
      }
      fillFour(ctx, points);
    };
    ctx.__orielFourPointPath = true;
    return ctx;
  }

  function squareConfetti(canvas, library) {
    if (!library || typeof library.create !== 'function') throw new Error('Load canvas-confetti 1.9.3 first');
    quadContext(canvas);
    var instance = library.create(canvas, {resize: false, useWorker: false});
    var disposed = false;
    function fire(options) {
      if (disposed) throw new Error('OrielShims: confetti instance is disposed');
      options = options || {};
      if (options.flat === false || options.resize || options.useWorker ||
          options.shapes && (options.shapes.length !== 1 || options.shapes[0] !== 'square')) {
        throw new Error('OrielShims: confetti requires flat:true, square shapes, resize:false and useWorker:false');
      }
      return instance(Object.assign({}, options, {flat: true, shapes: ['square']}));
    }
    fire.reset = function () { instance.reset(); };
    fire.dispose = function () {
      if (!disposed) {
        disposed = true;
        instance.reset();
        global.removeEventListener('unload', fire.dispose);
      }
    };
    global.addEventListener('unload', fire.dispose);
    return fire;
  }

  global.OrielShims = {
    quadContext: quadContext,
    squareConfetti: squareConfetti,
    capabilities: function () {
      return {version: 1, htmlNamespace: typeof document.createElementNS === 'function',
        reactIframeProbe: typeof global.HTMLIFrameElement === 'function',
        animeTypeProbes: typeof global.SVGElement === 'function' && typeof global.NodeList === 'function',
        canvas: 'four-point-fill-only', confetti: '1.9.3-flat-square'};
    }
  };
})(window);
