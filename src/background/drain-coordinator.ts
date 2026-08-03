export type DrainBatch<Item> = {
	items: Item[];
	hasMore: boolean;
};

export function createDrainCoordinator(runPass: () => Promise<void>): () => Promise<void> {
	let activeDrain: Promise<void> | null = null;
	let drainRequested = false;

	return function drain(): Promise<void> {
		drainRequested = true;
		if (activeDrain !== null) {
			return activeDrain;
		}

		activeDrain = (async () => {
			do {
				drainRequested = false;
				await runPass();
			} while (drainRequested);
		})().finally(() => {
			activeDrain = null;
		});

		return activeDrain;
	};
}

export async function drainAllBatches<Item>(loadBatch: () => Promise<DrainBatch<Item>>, processBatch: (items: Item[]) => Promise<void>): Promise<void> {
	while (true) {
		const batch = await loadBatch();
		if (batch.items.length === 0) {
			return;
		}

		await processBatch(batch.items);
		if (!batch.hasMore) {
			return;
		}
	}
}
