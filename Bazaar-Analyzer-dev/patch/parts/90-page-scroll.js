// ===== Page-scroll lists (parts/90-page-scroll.js) =====
// The Market table (and Watchlist), the AH item list and Book Flips used to scroll inside a box of fixed height
// (calc(100vh - N px)), which left dead space under the box. Now the box is as tall as its rows and the page itself
// scrolls: same @tanstack virtualizer (`so`), with the window as the scroll element.
//   listRef = the box, head = px from the box top to the first row (1 px border + a 38 px sticky header),
//   minW = the table's min width: when the box is narrower it scrolls sideways (the header then stops sticking).
// Returns { getTotalSize, getVirtualItems (start/end relative to the rows container), stickyTop, boxStyle }.

function BA_winRect(inst, cb) {
  const w = inst.scrollElement;
  if (!w) return;
  const h = () => cb({ width: w.innerWidth, height: w.innerHeight });
  h();
  w.addEventListener('resize', h, { passive: true });
  return () => w.removeEventListener('resize', h);
}

function BA_winOffset(inst, cb) {
  const w = inst.scrollElement;
  if (!w) return;
  let t = 0;
  const h = () => {
    cb(w.scrollY, true);
    clearTimeout(t);
    t = setTimeout(() => cb(w.scrollY, false), 150);
  };
  w.addEventListener('scroll', h, { passive: true });
  return () => {
    w.removeEventListener('scroll', h);
    clearTimeout(t);
  };
}

function BA_usePageVirt({ count, estimateSize, overscan, listRef, head = 1, minW = 0 }) {
  const [lay, setLay] = (0, y.useState)({ margin: 0, top: 0, narrow: false });
  (0, y.useLayoutEffect)(() => {
    const measure = () => {
      const el = listRef.current;
      const bar = document.querySelector('header');
      const next = {
        margin: el ? Math.round(el.getBoundingClientRect().top + window.scrollY) + head : 0,
        top: bar ? bar.offsetHeight : 0,
        narrow: minW > 0 && (el ? el.clientWidth : window.innerWidth) < minW,
      };
      setLay((o) => (o.margin === next.margin && o.top === next.top && o.narrow === next.narrow ? o : next));
    };
    measure();
    // Anything that moves the list (header wrap, filter bar, messages, window width) changes the body size.
    const ro = new ResizeObserver(measure);
    ro.observe(document.body);
    return () => ro.disconnect();
  }, [head, minW]);
  const v = so({
    count, estimateSize, overscan,
    scrollMargin: lay.margin,
    getScrollElement: () => window,
    observeElementRect: BA_winRect,
    observeElementOffset: BA_winOffset,
    initialOffset: () => window.scrollY,
  });
  const m = lay.margin;
  return {
    getTotalSize: () => v.getTotalSize(),
    getVirtualItems: () => v.getVirtualItems().map((it) => ({ ...it, start: it.start - m, end: it.end - m })),
    stickyTop: lay.top,
    // clip (not hidden/auto) keeps the rounded corners without making the box a scroll container, so the
    // sticky header still sticks to the window.
    boxStyle: lay.narrow ? { overflowX: 'auto' } : { overflow: 'clip' },
  };
}
