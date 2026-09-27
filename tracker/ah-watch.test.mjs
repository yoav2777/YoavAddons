import { test } from 'node:test';
import assert from 'node:assert/strict';
import { logged, mine } from './ah-watch.mjs';

test('keeps only my new sales', () => {
  const me = 'aaaa';
  const batch = [
    { auction_id: '1', seller: me, buyer: 'bbbb', price: 100, bin: true, timestamp: 1 },
    { auction_id: '2', seller: 'cccc', buyer: me, price: 50, bin: false, timestamp: 2 },
    { auction_id: '3', seller: 'cccc', buyer: 'dddd', price: 9, bin: true, timestamp: 3 },
    { auction_id: '4', seller: me, buyer: 'eeee', price: 7, bin: true, timestamp: 4 },
  ];
  const rows = mine(batch, me, new Set(['4']));
  assert.deepEqual(rows.map((r) => [r.auctionId, r.buyer]), [['1', 'bbbb']]);
});

test('ids already in the log count as seen', () => {
  assert.deepEqual([...logged('{"auctionId":"1"}\n{"auctionId":"2"}\n')], ['1', '2']);
});
