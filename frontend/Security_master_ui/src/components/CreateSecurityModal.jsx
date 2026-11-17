import React, { useState } from 'react';
import { X, PlusCircle } from 'lucide-react';

export default function CreateSecurityModal({ isOpen, onClose, onCreate }) {
  const [form, setForm] = useState({
    symbol: '',
    name: '',
    isin: '',
    exchange: 'NSE',
    type: 'EQUITY',
    currency: 'INR',
    price: '',
    feeBps: '40',
    haircutPct: '15.0'
  });

  if (!isOpen) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!form.symbol || !form.name || !form.isin || !form.price) return;
    const p = parseFloat(form.price);
    onCreate({
      id: `SEC-${Math.floor(1000 + Math.random() * 9000)}`,
      symbol: form.symbol.toUpperCase().trim(),
      name: form.name.trim(),
      isin: form.isin.toUpperCase().trim(),
      exchange: form.exchange,
      sector: form.type === 'ETF' ? 'Index ETF' : 'Equities',
      type: form.type,
      status: 'ACTIVE',
      currency: form.currency,
      lotSize: 1,
      price: p,
      prevClose: p,
      open: p,
      dayHigh: p,
      dayLow: p,
      bid: Number((p * 0.9995).toFixed(2)),
      ask: Number((p * 1.0005).toFixed(2)),
      volume: 100000,
      lendableQty: 1000000,
      borrowedQty: 250000,
      feeBps: parseInt(form.feeBps, 10) || 40,
      haircutPct: parseFloat(form.haircutPct) || 15.0,
      history: [p, p]
    });
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-xl max-w-md w-full p-5 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3 mb-4">
          <h3 className="text-base font-bold text-white flex items-center gap-2">
            <PlusCircle className="w-4 h-4 text-indigo-400" />
            Onboard New Security Master
          </h3>
          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3 text-xs">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-slate-400 block mb-1">Ticker Symbol *</label>
              <input
                required
                placeholder="e.g. WIPRO"
                value={form.symbol}
                onChange={(e) => setForm({ ...form, symbol: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-100 font-mono uppercase"
              />
            </div>
            <div>
              <label className="text-slate-400 block mb-1">ISIN *</label>
              <input
                required
                placeholder="INE075A01022"
                value={form.isin}
                onChange={(e) => setForm({ ...form, isin: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-100 font-mono uppercase"
              />
            </div>
          </div>

          <div>
            <label className="text-slate-400 block mb-1">Legal Security Name *</label>
            <input
              required
              placeholder="Wipro Limited"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-100"
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="text-slate-400 block mb-1">Asset Type</label>
              <select
                value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-2 text-slate-100"
              >
                <option value="EQUITY">EQUITY</option>
                <option value="ETF">ETF</option>
              </select>
            </div>
            <div>
              <label className="text-slate-400 block mb-1">Exchange</label>
              <select
                value={form.exchange}
                onChange={(e) => setForm({ ...form, exchange: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-2 text-slate-100"
              >
                <option value="NSE">NSE</option>
                <option value="BSE">BSE</option>
              </select>
            </div>
            <div>
              <label className="text-slate-400 block mb-1">Initial Price *</label>
              <input
                required
                type="number"
                step="0.01"
                placeholder="560.00"
                value={form.price}
                onChange={(e) => setForm({ ...form, price: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-2 text-slate-100 font-mono"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-slate-400 block mb-1">Default Fee (bps)</label>
              <input
                type="number"
                value={form.feeBps}
                onChange={(e) => setForm({ ...form, feeBps: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-100 font-mono"
              />
            </div>
            <div>
              <label className="text-slate-400 block mb-1">Collateral Haircut (%)</label>
              <input
                type="number"
                step="0.5"
                value={form.haircutPct}
                onChange={(e) => setForm({ ...form, haircutPct: e.target.value })}
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-slate-100 font-mono"
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 hover:bg-slate-700"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-4 py-1.5 rounded-lg bg-indigo-600 text-white font-semibold hover:bg-indigo-500"
            >
              Create Security
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
