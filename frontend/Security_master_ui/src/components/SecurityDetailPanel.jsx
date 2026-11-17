import React, { useState, useMemo } from 'react';
import { ShieldCheck, ArrowUpRight, ArrowDownRight, BookOpen, Layers } from 'lucide-react';
import IntradayChart from './IntradayChart';

export default function SecurityDetailPanel({ security, onToggleStatus, onManualPrice }) {
  const [overrideInput, setOverrideInput] = useState('');

  if (!security) return null;

  const diff = security.price - security.prevClose;
  const pct = ((diff / security.prevClose) * 100).toFixed(2);
  const isUp = diff >= 0;
  const utilization = ((security.borrowedQty / security.lendableQty) * 100).toFixed(1);
  const dayRangePct = Math.min(
    100,
    Math.max(0, ((security.price - security.dayLow) / (security.dayHigh - security.dayLow || 1)) * 100)
  );

  const depthLevels = useMemo(() => {
    const baseBid = security.bid ?? security.price * 0.9995;
    const baseAsk = security.ask ?? security.price * 1.0005;
    return [0, 1, 2].map((lvl) => ({
      bidQty: (lvl + 1) * 450 + ((security.volume % 300) * (lvl + 1)),
      bidPrice: (baseBid - lvl * 0.25).toFixed(2),
      askPrice: (baseAsk + lvl * 0.25).toFixed(2),
      askQty: (lvl + 1) * 410 + ((security.volume % 250) * (lvl + 1))
    }));
  }, [security]);

  const handleSubmit = (e) => {
    e.preventDefault();
    const val = parseFloat(overrideInput);
    if (!isNaN(val) && val > 0) {
      onManualPrice(security, val);
      setOverrideInput('');
    }
  };

  return (
    <aside className="bg-slate-900/70 border border-slate-800 rounded-xl p-5 flex flex-col gap-4">
      <div className="flex items-start justify-between border-b border-slate-800 pb-3.5">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-2xl font-bold text-white tracking-tight">{security.symbol}</h2>
            <span className="text-[11px] font-mono bg-slate-800 text-slate-300 px-2 py-0.5 rounded">
              {security.exchange}
            </span>
            <span
              className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                security.status === 'ACTIVE'
                  ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30'
                  : 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
              }`}
            >
              {security.status}
            </span>
          </div>
          <p className="text-sm text-slate-300 mt-0.5">{security.name}</p>
          <p className="text-xs font-mono text-slate-500 mt-1">
            ISIN: {security.isin} • SEDOL: {security.sedol || 'N/A'}
          </p>
        </div>

        <div className="text-right">
          <div className="text-2xl font-mono font-bold text-white">
            {security.currency} {security.price.toFixed(2)}
          </div>
          <div
            className={`inline-flex items-center gap-0.5 text-xs font-mono font-semibold ${
              isUp ? 'text-emerald-400' : 'text-rose-400'
            }`}
          >
            {isUp ? <ArrowUpRight className="w-3.5 h-3.5" /> : <ArrowDownRight className="w-3.5 h-3.5" />}
            {isUp ? '+' : ''}{diff.toFixed(2)} ({isUp ? '+' : ''}{pct}%)
          </div>
        </div>
      </div>

      <IntradayChart security={security} />

      <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 space-y-2.5 text-xs">
        <div className="grid grid-cols-4 gap-2 text-center border-b border-slate-800/80 pb-2">
          <div>
            <span className="text-[10px] text-slate-500 block">OPEN</span>
            <span className="font-mono text-slate-200">{(security.open ?? security.prevClose).toFixed(2)}</span>
          </div>
          <div>
            <span className="text-[10px] text-slate-500 block">HIGH</span>
            <span className="font-mono text-emerald-400">{security.dayHigh.toFixed(2)}</span>
          </div>
          <div>
            <span className="text-[10px] text-slate-500 block">LOW</span>
            <span className="font-mono text-rose-400">{security.dayLow.toFixed(2)}</span>
          </div>
          <div>
            <span className="text-[10px] text-slate-500 block">VOLUME</span>
            <span className="font-mono text-slate-200">{(security.volume / 1e6).toFixed(2)}M</span>
          </div>
        </div>

        <div>
          <div className="flex justify-between text-[10px] font-mono text-slate-400 mb-1">
            <span>{security.dayLow.toFixed(2)}</span>
            <span>Intraday Range Position</span>
            <span>{security.dayHigh.toFixed(2)}</span>
          </div>
          <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
            <div
              className="h-full bg-indigo-500 rounded-full transition-all duration-300"
              style={{ width: `${dayRangePct}%` }}
            />
          </div>
        </div>
      </div>

      <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 text-xs">
        <div className="flex items-center justify-between text-[11px] font-semibold uppercase tracking-wider text-slate-400 mb-2">
          <span className="flex items-center gap-1.5">
            <Layers className="w-3.5 h-3.5 text-indigo-400" />
            Order Book Depth (L2)
          </span>
          <span className="font-mono text-[10px] text-slate-500">
            Spread: {(Number(depthLevels[0].askPrice) - Number(depthLevels[0].bidPrice)).toFixed(2)}
          </span>
        </div>
        <div className="grid grid-cols-4 text-[10px] text-slate-500 font-mono pb-1 border-b border-slate-800/80">
          <span>BID QTY</span>
          <span className="text-right pr-2">BID</span>
          <span className="pl-2">ASK</span>
          <span className="text-right">ASK QTY</span>
        </div>
        <div className="divide-y divide-slate-800/40 font-mono text-[11px]">
          {depthLevels.map((lvl, idx) => (
            <div key={idx} className="grid grid-cols-4 py-1">
              <span className="text-slate-400">{lvl.bidQty.toLocaleString()}</span>
              <span className="text-right pr-2 text-emerald-400 font-medium">{lvl.bidPrice}</span>
              <span className="pl-2 text-rose-400 font-medium">{lvl.askPrice}</span>
              <span className="text-right text-slate-400">{lvl.askQty.toLocaleString()}</span>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2 flex items-center gap-1.5">
          <BookOpen className="w-3.5 h-3.5 text-indigo-400" />
          Securities Lending Specs
        </h3>
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div className="bg-slate-950/60 border border-slate-800/80 p-2.5 rounded-lg">
            <span className="text-slate-500 block text-[11px]">Pool Utilization</span>
            <span className="font-mono text-indigo-300 font-semibold mt-0.5 block">
              {utilization}% ({(security.borrowedQty / 1000).toFixed(0)}k / {(security.lendableQty / 1000).toFixed(0)}k)
            </span>
          </div>
          <div className="bg-slate-950/60 border border-slate-800/80 p-2.5 rounded-lg">
            <span className="text-slate-500 block text-[11px]">Fee / Collateral Haircut</span>
            <span className="font-mono text-amber-300 font-semibold mt-0.5 block">
              {security.feeBps} bps / {security.haircutPct.toFixed(1)}%
            </span>
          </div>
        </div>
      </div>

      <div className="border-t border-slate-800 pt-3.5 space-y-2.5">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
            Governance & Price Policy
          </span>
          <button
            onClick={() => onToggleStatus(security)}
            className={`text-xs px-2.5 py-1 rounded-md font-medium border transition ${
              security.status === 'ACTIVE'
                ? 'border-amber-500/40 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20'
                : 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20'
            }`}
          >
            {security.status === 'ACTIVE' ? 'Suspend Security' : 'Activate Security'}
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            type="number"
            step="0.01"
            value={overrideInput}
            onChange={(e) => setOverrideInput(e.target.value)}
            placeholder={`Manual price (${security.currency})...`}
            className="flex-1 bg-slate-950 border border-slate-800 rounded-lg px-3 py-1.5 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500"
          />
          <button
            type="submit"
            className="bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium px-3 py-1.5 rounded-lg transition"
          >
            Publish Tick
          </button>
        </form>
      </div>
    </aside>
  );
}
