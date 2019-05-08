package is.ru.cadia.ggp.propnet.bitsetstate;

import is.ru.cadia.ggp.propnet.structure.PropNetStructure;
import is.ru.cadia.ggp.propnet.structure.components.StaticComponent;

import java.util.BitSet;

public class ForwardPropagationStrategy {

	private PropNetStructure propNetStructure;

	public ForwardPropagationStrategy(PropNetStructure propNetStructure) {
		this.propNetStructure = propNetStructure;
	}

	private int computeValueAndPropagateChange(StaticComponent c, int nextComponentToCheck, InternalBitSetState state) {
		boolean value;
		switch (c.type) {
			case AND:
				value = true;
				for(int input:c.inputs) {
					value = value && state.get(input);
					if (!value) break;
				}
				break;
			case OR:
				value = false;
				for(int input:c.inputs) {
					value = value || state.get(input);
					if (value) break;
				}
				break;
			case NOT:
				value = !state.get(c.inputs[0]);
				break;
			case PIPE:
				value = state.get(c.inputs[0]);
				break;
			default:
				throw new RuntimeException("INIT, TRUE, FALSE, BASE and INPUT components should always have their computed bits sets");
		}
		state.setComputed(c.id);
		return setComponentValue(c, state, value, nextComponentToCheck);
	}

	/**
		 * propagate changes in components values until the values of all components
		 * in the given componentSet have been computed
		 * @param componentSet
		 * @param state
		 */
		public void propagateChanges(BitSet componentSet, InternalBitSetState state) {
			int nextId = componentSet.nextSetBit(0);
			int previousId;
			while (nextId != -1) {
				// find the next component that is in the component set and has its computed bit cleared
				do {
					previousId = nextId;
					nextId = state.computed.nextClearBit(nextId);
					nextId = componentSet.nextSetBit(nextId); // returns -1 if nextId is outside of the componentSet's range
				} while (nextId != -1 && nextId != previousId);
				// compute value of the component with id nextId and mark its outputs as tainted, if necessary
				if (nextId != -1) {
					nextId = computeValueAndPropagateChange(propNetStructure.getComponent(nextId), nextId+1, state);
				}
			}
		}

		public void propagateChanges(InternalBitSetState state) {
			int nextId = state.computed.nextClearBit(0);
			while (nextId < propNetStructure.getNbComponents()) {
				nextId = computeValueAndPropagateChange(propNetStructure.getComponent(nextId), nextId+1, state);
				nextId = state.computed.nextClearBit(nextId);
			}
		}

	/**
	 * sets the value of component c in state and marks outputs of c to be tainted if necessary
	 * @param c
	 * @param state
	 * @param value
	 * @return smallest ID of any component that has been tainted
	 */
	public int setComponentValue(StaticComponent c, InternalBitSetState state, boolean value) {
		return setComponentValue(c, state, value, propNetStructure.getNbComponents());
	}

	public int setComponentValue(StaticComponent c, InternalBitSetState state, boolean value, int nextComponentToCheck) {
		if (value != state.get(c.id)) {
			if (value) {
				return setTrue(state, c, nextComponentToCheck);
			} else {
				return setFalse(state, c, nextComponentToCheck);
			}
		}
		return nextComponentToCheck;
	}

	/**
	 * sets component c in state to false
	 *
	 * Assumes c was true before, also clears computed bits of all directly affected outputs of c.
	 * Resets value and computed bits of cyclic outputs of c if necessary to ensure minimal models
	 * in cyclic propnets.
	 * @param state
	 * @param c
	 */
	private int setFalse(InternalBitSetState state, StaticComponent c, int nextComponentToCheck) {
		state.clear(c.id);
		for (int output : c.outputs) {
			StaticComponent cout = propNetStructure.getComponent(output);
			switch (cout.type) {
			case PIPE:
			case AND:
				if (state.get(output)) {
					state.clearComputed(output);
					nextComponentToCheck = Math.min(nextComponentToCheck, output);
					// to deal with cyclic propnets
					if (cout.isCyclic) {
						nextComponentToCheck = setFalse(state, cout, nextComponentToCheck);
					}
				}
				break;
			case OR:
				if (state.get(output)) {
					state.clearComputed(output);
					nextComponentToCheck = Math.min(nextComponentToCheck, output);
					// to deal with cyclic propnets
					if (cout.isCyclic) {
						boolean outValue = false;
						for (int inId : cout.inputs) {
							if (!propNetStructure.getComponent(inId).isCyclic) {
								outValue = outValue || state.get(inId);
							}
						}
						if (!outValue) {
							nextComponentToCheck = setFalse(state, cout, nextComponentToCheck);
						}
					}
				}
				break;
			case NOT:
				if (!state.get(output)) {
					state.clearComputed(output);
				}
				break;
			default:
				assert false;
				break;
			}
		}
		return nextComponentToCheck;
	}

	/**
	 * sets component c in state to true
	 *
	 * assumes c was false before, also clears computed bits of all directly affected outputs of c
	 * @param state
	 * @param c
	 */
	private int setTrue(InternalBitSetState state, StaticComponent c, int nextComponentToCheck) {
		state.set(c.id);
		for (int output : c.outputs) {
			StaticComponent cout = propNetStructure.getComponent(output);
			switch (cout.type) {
			case PIPE:
			case AND:
				if (!state.get(output)) {
					state.clearComputed(output);
					nextComponentToCheck = Math.min(nextComponentToCheck, output);
				}
				break;
			case OR:
				if (!state.get(output)) {
					state.clearComputed(output);
					nextComponentToCheck = Math.min(nextComponentToCheck, output);
				}
				break;
			case NOT:
				if (state.get(output)) {
					state.clearComputed(output);
					nextComponentToCheck = Math.min(nextComponentToCheck, output);
				}
				break;
			default:
				assert false;
				break;
			}
		}
		return nextComponentToCheck;
	}

}
