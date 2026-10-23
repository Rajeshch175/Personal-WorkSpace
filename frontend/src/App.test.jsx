import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, test, vi } from 'vitest';
import App from './App.jsx';
import BackupPanel from './BackupPanel.jsx';

const loan = { id: 'loan-1', name: 'Test person', note: '', amount: 100, repaid: 0, outstanding: 100, dueDate: '2026-10-23', status: 'On track', repayments: [] };
const dashboard = { settings: { name: 'Test', intention: '' }, stats: { focusedMinutes: 0, habitsDone: 0, habitsTotal: 1, next30Days: 0, dayScore: 0, streak: 0 }, timeline: [], courses: [], finances: [], moneyCategories: {}, loans: [loan], habits: [{ id: 'habit-1', name: 'Walk', completed: false }], week: [] };
let writes;
beforeEach(() => {
  writes = [];
  vi.stubGlobal('fetch', vi.fn(async (url, options) => {
    if (options.method === 'GET') return { ok: true, status: 200, json: async () => structuredClone(dashboard) };
    writes.push({ url, ...options, body: options.body && JSON.parse(options.body) });
    return { ok: true, status: 200, json: async () => ({}) };
  }));
});
async function start() { const user = userEvent.setup(); render(<App />); await screen.findByText('Hello, Test'); return user; }
async function fill(user, label, value) { const input = screen.getByLabelText(label, { exact: true }); await user.clear(input); await user.type(input, value); }

test('time block form submits entered data', async () => {
  const user = await start(); await user.click(screen.getByRole('button', { name: '＋ Add entry' }));
  await fill(user, 'Activity', 'Read'); await fill(user, 'Start time', '09:00'); await fill(user, 'Duration (minutes)', '30');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true }));
  await waitFor(() => expect(writes).toHaveLength(1)); expect(writes[0].body).toMatchObject({ title: 'Read', time: '09:00', minutes: 30 });
});
test('course and recurring commitment forms send values', async () => {
  const user = await start(); await user.click(screen.getByRole('button', { name: /◇\s*Learning/ })); await user.click(screen.getByRole('button', { name: '＋ Add course' }));
  await fill(user, 'Name', 'Java'); await fill(user, 'Total units', '20'); await fill(user, 'Completed units', '4');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await screen.findByText('Hello, Test');
  expect(writes[0].body).toMatchObject({ name: 'Java', total: 20, completed: 4 });
  await user.click(screen.getByRole('button', { name: /⌁\s*Money/ })); await user.click(screen.getByRole('button', { name: '＋ Add commitment' }));
  await fill(user, 'Name', 'SIP'); await fill(user, 'Amount (INR)', '500'); await user.selectOptions(screen.getByLabelText('Repeats', { exact: true }), 'monthly');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await waitFor(() => expect(writes).toHaveLength(2));
  expect(writes[1].body).toMatchObject({ amount: 500, recurrence: 'monthly', status: 'scheduled' });
});
test('habit editing and settings submit updates', async () => {
  const user = await start(); await user.click(screen.getByRole('button', { name: 'Edit', exact: true })); await fill(user, 'Name', 'Run');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await screen.findByText('Hello, Test');
  expect(writes[0]).toMatchObject({ method: 'PUT', body: { name: 'Run' } });
  await user.click(screen.getByRole('button', { name: /⚙\s*Settings/ })); await fill(user, 'Your name', 'New name');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await waitFor(() => expect(writes).toHaveLength(2)); expect(writes[1].body.name).toBe('New name');
});
test('loan editing preserves endpoint and retries use the same repayment key', async () => {
  const user = await start(); await user.click(screen.getByRole('button', { name: /♧\s*People & lending/ })); await user.click(screen.getByRole('button', { name: 'Edit loan' }));
  await fill(user, 'Person name', 'Renamed'); await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await screen.findByText('Hello, Test');
  expect(writes[0].url).toContain('/loans/loan-1'); expect(writes[0].method).toBe('PUT');
  await user.click(screen.getByRole('button', { name: 'Log repayment' })); await fill(user, 'Amount (up to ₹100.00)', '25');
  fetch.mockImplementationOnce(async (url, options) => { writes.push({ url, ...options }); throw new Error('Connection lost'); });
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await screen.findByRole('alert');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await waitFor(() => expect(writes).toHaveLength(3));
  expect(writes[1].headers['Idempotency-Key']).toBe(writes[2].headers['Idempotency-Key']); expect(localStorage.length).toBe(0);
});
test('loading failure offers retry without showing fabricated records', async () => {
  fetch.mockRejectedValueOnce(new Error('Unavailable')); const user = userEvent.setup(); render(<App />);
  await screen.findByRole('alert'); expect(screen.queryByText('Test person')).toBeNull(); await user.click(screen.getByRole('button', { name: 'Retry' })); await screen.findByText('Hello, Test');
});
test('restore previews records and requires explicit confirmation', async () => {
  const user = userEvent.setup(), api = vi.fn().mockResolvedValue({}), restored = vi.fn();
  const backup = { version: 1, settings: {}, records: { 'time-blocks': [], courses: [], finances: [], loans: [], habits: [] } };
  render(<BackupPanel api={api} onRestore={restored} />);
  const file = new File([JSON.stringify(backup)], 'backup.json', { type: 'application/json' }); file.text = async () => JSON.stringify(backup);
  await user.upload(screen.getByLabelText('Backup file'), file); await screen.findByText('Backup contents');
  expect(screen.getByRole('button', { name: 'Restore backup' }).disabled).toBe(true);
  await user.click(screen.getByLabelText('Replace all current workspace data with this backup')); await user.click(screen.getByRole('button', { name: 'Restore backup' }));
  await waitFor(() => expect(restored).toHaveBeenCalled()); expect(api).toHaveBeenCalledWith('/backup/restore', 'POST', backup);
});

test('new loan and habit forms submit entered values', async () => {
  const user = await start(); await user.click(screen.getByRole('button', { name: '＋ Add habit' })); await fill(user, 'Name', 'Read daily');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await screen.findByText('Hello, Test');
  expect(writes[0]).toMatchObject({ method: 'POST', body: { name: 'Read daily' } });
  await user.click(screen.getByRole('button', { name: /♧\s*People & lending/ })); await user.click(screen.getByRole('button', { name: '＋ Add loan' }));
  await fill(user, 'Person name', 'Someone'); await fill(user, 'Amount lent (INR)', '200');
  await user.click(screen.getByRole('button', { name: 'Save', exact: true })); await waitFor(() => expect(writes).toHaveLength(2)); expect(writes[1].body).toMatchObject({ name: 'Someone', amount: 200 });
});
test('mark paid sends the displayed due date instead of replacing history', async () => {
  fetch.mockImplementationOnce(async () => ({ ok: true, status: 200, json: async () => ({ ...dashboard, finances: [{ id: 'finance-1', name: 'SIP', date: '2026-10-23', amount: 500, category: 'investment', recurrence: 'monthly', status: 'scheduled', payments: [] }] }) }));
  const user = await start(); await user.click(screen.getByRole('button', { name: /⌁\s*Money/ })); await user.click(screen.getByRole('button', { name: 'Mark paid' }));
  await waitFor(() => expect(writes).toHaveLength(1)); expect(writes[0].url).toContain('/finances/finance-1/payments'); expect(writes[0].body).toEqual({ dueDate: '2026-10-23' });
});
test('invalid backup shows an error without calling restore', async () => {
  const user = userEvent.setup(), api = vi.fn(); render(<BackupPanel api={api} onRestore={vi.fn()} />);
  const file = new File(['bad'], 'backup.json', { type: 'application/json' }); file.text = async () => 'bad';
  await user.upload(screen.getByLabelText('Backup file'), file); await screen.findByRole('alert'); expect(api).not.toHaveBeenCalled(); expect(screen.queryByRole('button', { name: 'Restore backup' })).toBeNull();
});
