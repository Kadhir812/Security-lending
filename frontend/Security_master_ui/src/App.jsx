import React, { useEffect, useMemo, useState } from 'react';
import { AlertCircle, Layers, Search } from 'lucide-react';
import { TARGET_SECURITIES } from './data/seedSecurities';
import { fetchSecurityByIsin } from './services/securityMasterApi';

function normalizeSecurity(security) {
  return {
    id: String(security.id ?? security.securityId),
    symbol: security.symbol,
    name: security.name ?? security.securityName,
    isin: security.isin,
    sedol: security.sedol,
    exchange: security.exchange ?? security.exchangeName,
    sector: security.sector,
    type: security.type ?? security.securityType,
    status: security.status,
    currency: security.currency,
    lotSize: security.lotSize
  };
}

export default function App() {
  const [securities, setSecurities] = useState([]);
  const [selectedIsin, setSelectedIsin] = useState(TARGET_SECURITIES[0].isin);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all(TARGET_SECURITIES.map(({ isin }) => fetchSecurityByIsin(isin)))
      .then((results) => {
        if (!active) return;
        const fixedSecurities = results.map(normalizeSecurity);
        setSecurities(fixedSecurities);
        setSelectedIsin(fixedSecurities[0]?.isin ?? '');
      })
      .catch((requestError) => {
        if (active) setError(`Could not load Infosys and TCS from Security Master: ${requestError.message}`);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const filteredSecurities = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) return securities;
    return securities.filter((security) =>
      [security.symbol, security.name, security.isin, security.sedol, security.sector]
        .some((value) => String(value ?? '').toLowerCase().includes(query))
    );
  }, [search, securities]);

  const selectedSecurity =
    filteredSecurities.find((security) => security.isin === selectedIsin)
    ?? filteredSecurities[0]
    ?? null;

  return (
    <main className="min-h-screen bg-slate-950 text-slate-100">
      <header className="border-b border-slate-800 bg-slate-950/90 px-6 py-5">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="rounded-lg border border-indigo-500/30 bg-indigo-500/15 p-2 text-indigo-300">
              <Layers className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-white">Security Master</h1>
              <p className="text-xs text-slate-400">Fixed reference details · Infosys and TCS</p>
            </div>
          </div>
          <span className="rounded-full border border-slate-700 bg-slate-900 px-3 py-1 text-xs text-slate-300">
            Market prices are not loaded in this phase
          </span>
        </div>
      </header>

      <section className="mx-auto max-w-7xl space-y-5 p-6">
        <label className="relative block max-w-lg">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search symbol, name, ISIN, SEDOL, or sector"
            className="w-full rounded-lg border border-slate-800 bg-slate-900 py-2 pl-9 pr-3 text-sm text-slate-100 outline-none focus:border-indigo-500"
          />
        </label>

        {error && (
          <div role="alert" className="flex items-start gap-2 rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-sm text-rose-200">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{error}. Enable MARKET_SEED_ENABLED and restart the backend if these records have not been seeded.</span>
          </div>
        )}

        <div className="overflow-x-auto rounded-xl border border-slate-800 bg-slate-900/60">
          <table className="w-full min-w-[900px] text-left text-sm">
            <thead className="bg-slate-950/70 text-xs uppercase tracking-wide text-slate-400">
              <tr>
                <th className="px-4 py-3">Security</th>
                <th className="px-4 py-3">ISIN</th>
                <th className="px-4 py-3">SEDOL</th>
                <th className="px-4 py-3">Exchange</th>
                <th className="px-4 py-3">Sector</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Currency</th>
                <th className="px-4 py-3">Lot size</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {filteredSecurities.map((security) => (
                <tr
                  key={security.isin}
                  onClick={() => setSelectedIsin(security.isin)}
                  className={`cursor-pointer ${selectedSecurity?.isin === security.isin ? 'bg-indigo-500/10' : 'hover:bg-slate-800/50'}`}
                >
                  <td className="px-4 py-3">
                    <div className="font-semibold text-white">{security.symbol}</div>
                    <div className="text-xs text-slate-400">{security.name}</div>
                  </td>
                  <td className="px-4 py-3 font-mono text-slate-300">{security.isin}</td>
                  <td className="px-4 py-3 font-mono text-slate-300">{security.sedol ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-300">{security.exchange ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-300">{security.sector ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-300">{security.type ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-300">{security.currency ?? '—'}</td>
                  <td className="px-4 py-3 text-slate-300">{security.lotSize ?? '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded-full px-2 py-1 text-[11px] ${security.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-300' : 'bg-slate-800 text-slate-300'}`}>
                      {security.status ?? '—'}
                    </span>
                  </td>
                </tr>
              ))}
              {!loading && filteredSecurities.length === 0 && (
                <tr>
                  <td colSpan="9" className="px-4 py-10 text-center text-sm text-slate-400">
                    {error ? 'No backend data was loaded.' : 'No matching security records.'}
                  </td>
                </tr>
              )}
              {loading && (
                <tr>
                  <td colSpan="9" className="px-4 py-10 text-center text-sm text-slate-400">
                    Loading fixed security details…
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {selectedSecurity && (
          <p className="text-xs text-slate-500">
            Security ID: <span className="font-mono">{selectedSecurity.id}</span>
          </p>
        )}
      </section>
    </main>
  );
}
