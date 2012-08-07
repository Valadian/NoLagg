package com.bergerkiller.bukkit.nolagg.chunks;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.WorldServer;

import org.bukkit.craftbukkit.CraftChunk;

import com.bergerkiller.bukkit.common.Operation;
import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.nolagg.NoLagg;
import com.bergerkiller.bukkit.nolagg.chunks.antiloader.DummyPlayerManager;

public class DynamicViewDistance {
	public static int viewDistance = CommonUtil.view;
	private static int chunks = 0;
	private static boolean chunksChanged = false;
	private static Map<Integer, Integer> nodes = new LinkedHashMap<Integer, Integer>();
	private static Task task;

	public static void addChunk() {
		chunks++;
		chunksChanged = true;
	}

	public static void removeChunk() {
		chunks--;
		chunksChanged = true;
	}

	public static boolean isNear(ChunkSendQueue queue, int x, int z) {
		if (task == null) {
			return true;
		} else {
			return queue.isNear(x, z, viewDistance - 1);
		}
	}

	public static void init(List<String> elements) {
		nodes.clear();
		viewDistance = CommonUtil.view;
		chunks = 0;
		Task.stop(task);
		task = null;
		if (!NoLaggChunks.useDynamicView) {
			return;
		}
		int lowest = Integer.MAX_VALUE;
		Iterator<String> iter = elements.iterator();
		while (iter.hasNext()) {
			try {
				String[] elem = iter.next().split("=");
				if (elem.length == 2) {
					int chunks = Integer.parseInt(elem[0].trim());
					int view = Integer.parseInt(elem[1].trim());
					if (view >= 2 && chunks >= 0) {
						nodes.put(chunks, view);
						lowest = Math.min(lowest, view);
						continue;
					}
				}
			} catch (Exception ex) {}
			iter.remove();
		}
		if (nodes.isEmpty() || lowest >= CommonUtil.view) {
			NoLaggChunks.useDynamicView = false;
			return;
		}
		chunks = 0;
		new Operation() {
			public void run() {
				this.doChunks();
			}
			public void handle(CraftChunk chunk) {
				chunks++;
			}
		};
		chunksChanged = true;

		// Alter player manager to prevent chunk loading outside range
		new Operation() {
			public void run() {
				this.doWorlds();
			}
			public void handle(WorldServer world) {
				DummyPlayerManager.convert(world);
			}
		};

		task = new Task(NoLagg.plugin) {
			public void run() {
				if (chunksChanged) {
					chunksChanged = false;
				} else {
					return;
				}
				// Get the new view distance
				int minChunks = 0, maxChunks = Integer.MAX_VALUE;
				int minView = 0, maxView = Integer.MAX_VALUE;

			    // Get min and max chunks and view around current chunks value
				for (Map.Entry<Integer, Integer> entry : nodes.entrySet()) {
					if (entry.getKey() <= chunks) {
						if (entry.getKey() >= minChunks) {
							minChunks = entry.getKey();
							minView = entry.getValue();
						}
					}
					if (entry.getKey() >= chunks) {
						if (entry.getKey() <= maxChunks) {
							maxChunks = entry.getKey();
							maxView = entry.getValue();
						}
					}
				}
				if (minView == 0) {
					minView = maxView;
				}
				if (maxView == Integer.MAX_VALUE) {
					maxView = minView;
				}
				if (minChunks == maxChunks) {
					viewDistance = minView;
				} else {
					double value = (double) (chunks - minChunks) / (double) (maxChunks - minChunks);
					viewDistance = (int) (value * (double) maxView + (1.00 - value) * (double) minView);
				}
				viewDistance = MathUtil.limit(viewDistance, 3, CommonUtil.view);
				
			}
		}.start(15, 40);
	}

	public static void deinit() {
		Task.stop(task);
		task = null;
		DummyPlayerManager.revert();
	}
}