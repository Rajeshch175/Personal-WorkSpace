import React, { useState } from 'react';

export default function BackupPanel({ api, onRestore }) {
  const [backup, setBackup] = useState(null);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  async function download() {
    setBusy(true); setError('');
    try {
      const data = await api('/backup');
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }));
      const link = document.createElement('a'); link.href = url; link.download = `workspace-${new Date().toISOString().slice(0, 10)}.json`; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setMessage('Backup downloaded. Keep it somewhere safe; it contains your workspace records.');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function choose(event) {
    setBackup(null); setConfirmed(false); setError(''); setMessage('');
    const file = event.target.files[0]; if (!file) return;
    try {
      if (file.size > 10 * 1024 * 1024) throw new Error('Choose a backup smaller than 10 MB.');
      const data = JSON.parse(await file.text());
      const kinds = ['time-blocks', 'courses', 'finances', 'loans', 'habits'];
      if (data.version !== 1 || !data.settings || !data.records || Object.keys(data.records).length !== kinds.length || kinds.some(kind => !Array.isArray(data.records[kind]))) throw new Error('This is not a supported workspace backup.');
      setBackup(data);
    } catch (e) { setError(e instanceof SyntaxError ? 'The file is not valid JSON.' : e.message); }
  }
  async function restore() {
    if (!backup || !confirmed || busy) return;
    setBusy(true); setError('');
    try { await api('/backup/restore', 'POST', backup); onRestore(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <section className="card backup-panel"><span className="eyebrow">YOUR DATA</span><h2>Backup & restore</h2><p>Download all records, settings, completion dates, and payment histories as a JSON file.</p><button className="primary-btn section-space" disabled={busy} onClick={download}>Download backup</button>
    <h3 className="section-space">Restore a backup</h3><p>Restore replaces the entire current workspace. Download a backup of your current data first.</p>
    <label className="form-field">Backup file<input type="file" accept=".json,application/json" disabled={busy} onChange={choose} /></label>
    {backup && <><h3>Backup contents</h3><ul>{Object.entries(backup.records).map(([kind, records]) => <li key={kind}>{kind}: {records.length}</li>)}</ul><label className="restore-confirm"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} /> Replace all current workspace data with this backup</label><button className="outline-btn section-space" disabled={!confirmed || busy} onClick={restore}>{busy ? 'Restoring…' : 'Restore backup'}</button></>}
    {error && <p role="alert" className="form-error section-space">{error}</p>}{message && <p role="status" className="section-space">{message}</p>}
  </section>;
}
