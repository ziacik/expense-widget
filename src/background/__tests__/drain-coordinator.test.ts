import { describe, expect, it, vi } from "vitest";

import { createDrainCoordinator, drainAllBatches } from "../drain-coordinator";

function deferred(): {
	promise: Promise<void>;
	resolve: () => void;
} {
	let resolve!: () => void;
	const promise = new Promise<void>((resolvePromise) => {
		resolve = resolvePromise;
	});

	return { promise, resolve };
}

describe("createDrainCoordinator", () => {
	it("runs another pass when a request arrives during the active pass", async () => {
		const firstPass = deferred();
		const runPass = vi.fn(async () => {
			if (runPass.mock.calls.length === 1) {
				await firstPass.promise;
			}
		});
		const drain = createDrainCoordinator(runPass);

		const firstDrain = drain();
		const coalescedDrain = drain();
		firstPass.resolve();

		await Promise.all([firstDrain, coalescedDrain]);

		expect(coalescedDrain).toBe(firstDrain);
		expect(runPass).toHaveBeenCalledTimes(2);
	});
});

describe("drainAllBatches", () => {
	it("continues beyond five full batches until the inbox is empty", async () => {
		let remainingBatches = 7;
		const processBatch = vi.fn(async (_items: number[]) => undefined);

		await drainAllBatches(async () => {
			const hasMore = remainingBatches > 1;
			remainingBatches -= 1;
			return { items: [remainingBatches], hasMore };
		}, processBatch);

		expect(processBatch).toHaveBeenCalledTimes(7);
	});
});
