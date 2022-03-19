/*
 * Copyright 2019 Distributed Systems Group
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package simblock.simulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import simblock.node.Node;
import simblock.settings.SimulationConfiguration.NetworkPartioningEvent;
import simblock.settings.SimulationConfiguration.NetworkPartioningEvent.Quantifier;
import simblock.task.Task;

import static simblock.settings.SimulationConfiguration.NUM_OF_NODES;
import static simblock.simulator.Simulator.getSimulatedNodes;
import static simblock.simulator.Simulator.getDownNodes;;

/**
 * The type Timer schedules the execution of simulation tasks stored in a Future
 * Event List (FEL) . Each {@link Task} can be scheduled for execution. Tasks
 * that have been run get removed from the FEL.
 */
public class Timer {

	/**
	 * A sorted queue of scheduled tasks.
	 */
	private static final PriorityQueue<ScheduledTask> taskQueue = new PriorityQueue<>();

	/**
	 * A map containing a mapping of all tasks to their ScheduledTask counterparts.
	 * When executed, the key - value pair is to be removed from the mapping.
	 */
	// TODO a bit redundant since Task is again stored in ScheduledTask. Is there a
	// better approach?
	private static final Map<Task, ScheduledTask> taskMap = new HashMap<>();
	/**
	 * Initial simulation time in milliseconds.
	 */
	// TODO is it milliseconds?
	private static long currentTime = 0L;

	/**
	 * Represents a {@link Task} that is scheduled to be executed.
	 */
	private static class ScheduledTask implements Comparable<ScheduledTask> {
		private final Task task;
		private final long scheduledTime;

		/**
		 * Instantiates a new ScheduledTask.
		 *
		 * @param task          - the task to be executed
		 * @param scheduledTime - the simulation time at which the task is to be
		 *                      executed
		 */
		private ScheduledTask(Task task, long scheduledTime) {
			this.task = task;
			this.scheduledTime = scheduledTime;
		}

		/**
		 * Gets the task.
		 *
		 * @return the {@link Task} instance
		 */
		private Task getTask() {
			return this.task;
		}

		/**
		 * Gets the scheduled time at which the task is to be executed.
		 *
		 * @return the scheduled time
		 */
		private long getScheduledTime() {
			return this.scheduledTime;
		}

		/**
		 * Compares the two scheduled tasks.
		 *
		 * @param o other task
		 * @return 1 if self is executed later, 0 if concurrent and -1 if self is to be
		 *         executed before.
		 */
		public int compareTo(ScheduledTask o) {
			if (this.equals(o)) {
				return 0;
			}
			int order = Long.signum(this.scheduledTime - o.scheduledTime);
			if (order != 0) {
				return order;
			}
			order = System.identityHashCode(this) - System.identityHashCode(o);
			return order;
		}
	}

	/**
	 * Runs a {@link ScheduledTask}.
	 */
	public static void runTask() {
		// If there are any tasks
		if (taskQueue.size() > 0) {
			// Get the next ScheduledTask
			ScheduledTask currentScheduledTask = taskQueue.poll();
			Task currentTask = currentScheduledTask.getTask();
			currentTime = currentScheduledTask.getScheduledTime();
			// Remove the task from the mapping of all tasks
			taskMap.remove(currentTask, currentScheduledTask);
			// Execute
			currentTask.run();
		}
	}

	/**
	 * Remove task from the mapping of all tasks and from the execution queue.
	 *
	 * @param task the task to be removed
	 */
	public static void removeTask(Task task) {
		if (taskMap.containsKey(task)) {
			ScheduledTask scheduledTask = taskMap.get(task);
			taskQueue.remove(scheduledTask);
			taskMap.remove(task, scheduledTask);
		}
	}

	/**
	 * Runs a {@link ScheduledTask}.
	 * 
	 * @throws Exception
	 */
	public static void performPartioning(NetworkPartioningEvent event) throws Exception {
		List<Integer> nodeIndexes = getPartioningNodes(event.getQuantity(), event.getQuantifier());
		//List<Node> nodesRemoved= new ArrayList<>();
		for (int nodei : nodeIndexes) {
			Node node = getSimulatedNodes().get(nodei);
			ArrayList<Node> neighbours = node.getNeighbors();
			for(Node neighbour : neighbours) {
				node.removeNeighbor(neighbour);
			}
			//nodesRemoved.add(node);
			getDownNodes().add(node);
		}
		
		/*for( Node node: nodesRemoved)
			getSimulatedNodes().remove(node);*/
	}

	private static List<Integer> getPartioningNodes(Integer quantity, Quantifier quantifier) throws Exception {
		List<Integer> nodeIndexes = new ArrayList<Integer>();
		Integer totalNodesGettingPartitioned;
		if (quantifier.equals(Quantifier.COUNT)) {
			totalNodesGettingPartitioned = quantity;
		} else if (quantifier.equals(Quantifier.PERCENTAGE)) {
			totalNodesGettingPartitioned = quantity * (getSimulatedNodes().size()) / 100;
		} else {
			throw new Exception("Invalid Quantifier");
		}

		while (nodeIndexes.size() < getSimulatedNodes().size()) {
			nodeIndexes.add(nodeIndexes.size());
		}

		Collections.shuffle(nodeIndexes, new Random());

		return nodeIndexes.subList(0, quantity);
	}

	/**
	 * Get the {@link Task} from the execution queue to be executed next.
	 *
	 * @return the task from the queue or null if task queue is empty.
	 */
	public static Task getTask() {
		if (taskQueue.size() > 0) {
			ScheduledTask currentTask = taskQueue.peek();
			return currentTask.getTask();
		} else {
			return null;
		}
	}

	/**
	 * Schedule task to be executed at the current time incremented by the task
	 * duration.
	 *
	 * @param task the task
	 */
	public static void putTask(Task task) {
		ScheduledTask scheduledTask = new ScheduledTask(task, currentTime + task.getInterval());
		taskMap.put(task, scheduledTask);
		taskQueue.add(scheduledTask);
	}

	/**
	 * Schedule task to be executed at the provided absolute timestamp.
	 *
	 * @param task the task
	 * @param time the time in milliseconds
	 */
	@SuppressWarnings("unused")
	public static void putTaskAbsoluteTime(Task task, long time) {
		ScheduledTask scheduledTask = new ScheduledTask(task, time);
		taskMap.put(task, scheduledTask);
		taskQueue.add(scheduledTask);
	}

	/**
	 * Get current time in milliseconds.
	 *
	 * @return the time
	 */
	public static long getCurrentTime() {
		return currentTime;
	}
}
