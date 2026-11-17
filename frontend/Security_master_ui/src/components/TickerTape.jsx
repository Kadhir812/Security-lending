import React from 'react';
import { Radio, Clock, ShieldAlert, RefreshCw } from 'lucide-react';

export default function TickerTape({
  securities,
  streamStatus,
  nextBatchIn,
  quotaUsed,
  onSelect,
  onManualSync,
  isSyncing
}) {
  const tapeItems = [...securities, ...securities];

  return (
    <div className="bg-slate-900 border-b border-slate-800 px-4 py-1.5 flex items-center justify-between text-xs gap-4">
      <div className="flex items-center gap-2 shrink-0 pr-3 border-r border-slate-800">
        <span className="flex items-center gap-1.5 font-semibold text-emerald-400">
          <Radio className="w-3.5 h-3.5 animate-pulse" />
          {streamStatus === 'LIVE_SSE' ? 'SSE LIVE' : 'SIMULATED FEED'}
        </span>
      </div>

      <div className="overflow-hidden flex-1">
        <div className="animate-ticker gap-6">
          {tapeItems.map((sec, idx) => {
            const diff = sec.price - sec.prevClose;
            const pct = ((diff / sec.prevClose) * 100).toFixed(2);
            const isUp = diff >= 0;
            return (
              <button
                key={`${sec.id}-${idx}`}
                onClick={() => onSelect(sec.id)}
                className="inline-flex items-center gap-2 px-2 py-0.5 rounded hover:bg-slate-800/80 transition whitespace-nowrap"
              >
                <span className="font-bold text-slate-200">{sec.symbol}</span>
                <span className="font-mono text-slate-300">{sec.price.toFixed(2)}</span>
                <span className={`font-mono font-medium ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {isUp ? '▲' : '▼'} {Math.abs(Number(pct)).toFixed(2)}%
                </span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="shrink-0 flex items-center gap-2.5 pl-3 border-l border-slate-800 text-slate-400">
        <div className="flex items-center gap-1.5 bg-slate-950 border border-slate-800 px-2.5 py-0.5 rounded-full">
          <Clock className="w-3 h-3 text-indigo-400" />
          <span>Sync in:</span>
          <strong className="font-mono text-slate-200">{nextBatchIn}s</strong>
        </div>

        <button
          onClick={onManualSync}
          disabled={isSyncing || quotaUsed >= 58}
          title="Execute immediate 1-request Yahoo batch sync (consumes 1 hourly quota unit)"
          className="flex items-center gap-1 bg-indigo-500/15 hover:bg-indigo-500/25 disabled:opacity-40 text-indigo-300 border border-indigo-500/30 px-2.5 py-0.5 rounded-full transition"
        >
          <RefreshCw className={`w-3 h-3 ${isSyncing ? 'animate-spin' : ''}`} />
          <span>Sync Now</span>
        </button>

        <div
          title="Yahoo Finance rate-limit guard: 1 batched request per minute = max 60 req/hr"
          className="hidden md:flex items-center gap-1.5 bg-slate-950 border border-slate-800 px-2.5 py-0.5 rounded-full"
        >
          <ShieldAlert className="w-3 h-3 text-amber-400" />
          <span>Quota:</span>
          <strong className="font-mono text-amber-300">{quotaUsed}/60</strong>
          <span className="text-slate-500">req/hr</span>
        </div>
      </div>
    </div>
  );
}
