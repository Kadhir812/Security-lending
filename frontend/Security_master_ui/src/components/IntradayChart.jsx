import React, { useState } from 'react';

export default function IntradayChart({ security }) {
  const [hoverIdx, setHoverIdx] = useState(null);
  const data = security.history || [security.price];

  if (data.length < 2) return null;

  const min = Math.min(...data) * 0.9992;
  const max = Math.max(...data) * 1.0008;
  const range = max - min || 1;
  const w = 540;
  const h = 168;

  const isUp = data[data.length - 1] >= security.prevClose;
  const strokeColor = isUp ? '#10b981' : '#f43f5e';

  const coords = data.map((val, i) => ({
    x: (i / (data.length - 1)) * w,
    y: h - ((val - min) / range) * (h - 24) - 12,
    val
  }));

  const prevCloseY = Math.min(
    h - 6,
    Math.max(6, h - ((security.prevClose - min) / range) * (h - 24) - 12)
  );

  const linePath = coords.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ');
  const areaPath = `${linePath} L ${w} ${h} L 0 ${h} Z`;
  const activePoint = hoverIdx !== null ? coords[hoverIdx] : coords[coords.length - 1];

  return (
    <div className="bg-slate-950/80 border border-slate-800 rounded-xl p-3.5">
      <div className="flex justify-between items-center text-xs mb-2">
        <div className="flex items-center gap-2">
          <span className="text-slate-400 font-medium">Intraday Movement</span>
          <span className="font-mono text-slate-200 bg-slate-900 px-2 py-0.5 rounded border border-slate-800">
            {security.currency} {activePoint.val.toFixed(2)}
          </span>
        </div>
        <span className="text-[11px] font-mono text-slate-500">
          Prev Close: {security.prevClose.toFixed(2)}
        </span>
      </div>

      <svg
        viewBox={`0 0 ${w} ${h}`}
        className="w-full h-40 overflow-visible cursor-crosshair"
        onMouseLeave={() => setHoverIdx(null)}
      >
        <defs>
          <linearGradient id={`area-${security.id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={strokeColor} stopOpacity="0.28" />
            <stop offset="100%" stopColor={strokeColor} stopOpacity="0.0" />
          </linearGradient>
        </defs>

        <line
          x1="0"
          y1={prevCloseY}
          x2={w}
          y2={prevCloseY}
          stroke="#475569"
          strokeDasharray="4 4"
          strokeWidth="1"
        />

        <path d={areaPath} fill={`url(#area-${security.id})`} />
        <path d={linePath} fill="none" stroke={strokeColor} strokeWidth="2.2" />

        {coords.map((pt, idx) => (
          <rect
            key={idx}
            x={pt.x - w / coords.length / 2}
            y={0}
            width={w / coords.length}
            height={h}
            fill="transparent"
            onMouseEnter={() => setHoverIdx(idx)}
          />
        ))}

        <circle
          cx={activePoint.x}
          cy={activePoint.y}
          r="5"
          fill={strokeColor}
          fillOpacity="0.35"
          className="animate-ping"
        />
        <circle cx={activePoint.x} cy={activePoint.y} r="3.5" fill={strokeColor} />
      </svg>
    </div>
  );
}
