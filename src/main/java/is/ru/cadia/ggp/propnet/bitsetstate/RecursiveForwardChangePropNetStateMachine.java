package is.ru.cadia.ggp.propnet.bitsetstate;

import is.ru.cadia.ggp.propnet.structure.PropNetStructure;
import is.ru.cadia.ggp.propnet.structure.PropNetStructureFactory;
import is.ru.cadia.ggp.propnet.structure.components.StaticComponent;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;

/**
 * propagate changes of components' values forward through the network
 *
 * instead of going through components in topological order, we immediately
 * propagate the value of a component to its outputs (recursively).
 * This requires less bookkeeping, but may incur some additional work.
 *
 * the idea is to use the "computed" bit in a state to mean whether a component's
 * value has been computed and the output components have been marked as potentially changed (by
 * clearing their computed bits)
 */
public class RecursiveForwardChangePropNetStateMachine extends PropNetBitSetStateMachine {


	public RecursiveForwardChangePropNetStateMachine(PropNetStructureFactory factory) {
		super(factory);
	}

	public RecursiveForwardChangePropNetStateMachine(PropNetStructure propnetStructure) {
		super(propnetStructure);
	}

	@Override
	protected InternalBitSetState makeBaseState() {
		InternalBitSetState state = super.makeBaseState();
		// ensure consistency of all components' values with the values of their inputs
		Stopwatch stopWatch = new Stopwatch();
		stopWatch.start();
		ensureConsistency(state);
		stopWatch.stop();
		System.out.println("ensuring consistency of initial state took: " + stopWatch.elapsed(TimeUnit.MILLISECONDS)/1000.0 + "s");
		return state;
	}

	private void ensureConsistency(InternalBitSetState state) {
		// We compute each of the components values once using full forward propagation.
		// Since we do this in the topological order, fps.propagateChanges should take
		// no more than linear time in the number of components, unless the propnet has cycles.
		ForwardPropagationStrategy fps = new ForwardPropagationStrategy(propNetStructure);
		fps.propagateChanges(state);
	}

	/**
	 * sets the value of component c in state and propagates that change to all outputs of c
	 *
	 * assumes that the previous value of c was different from value
	 * @param c
	 * @param state
	 * @param value
	 * @return smallest ID of any component that has been tainted
	 */
	private void setComponentValue(StaticComponent c, InternalBitSetState state, boolean value) {
		state.set(c.id, value);
		for (int output : c.outputs) {
			StaticComponent cout = propNetStructure.getComponent(output);
			setComponentFromInput(cout, state, value);
		}
	}

	private void setComponentFromInput(StaticComponent c, InternalBitSetState state, boolean newInputValue) {
		boolean value = state.get(c.id);
		switch (c.type) {
		case PIPE:
			if (newInputValue!=value) setComponentValue(c, state, newInputValue);
			break;
		case AND:
			if (!value && newInputValue) {
				// check with the remaining inputs if they are all true now
				value = true;
				for(int input:c.inputs) {
					if (!state.get(input)) {
						value = false;
						break;
					}
				}
				if (value) {
					setComponentValue(c, state, true);
				}
			} else if (value && !newInputValue) {
				setComponentValue(c, state, false);
			}
			break;
		case OR:
			if (!value && newInputValue) {
				setComponentValue(c, state, true);
			} else if (value && !newInputValue) {
				if (c.isCyclic) {
					// We first propagate the value within this cycle as if the cyclic inputs don't exist.
					// This might leave some of the cyclic components inconsistent.
					// (their value is false in the state, but they have a true input that is in the cycle).
					// If we get to a component in the cycle whose value stays true, we need to still check
					// whether any of the components outputs are inconsistent and change+propagate their value.
					value = false;
					for (int inId : c.inputs) {
						if (!propNetStructure.getComponent(inId).isCyclic && state.get(inId)) {
							value = true;
							break;
						}
					}
					if (!value) {
						setComponentValue(c, state, false);
						// Now that we have propagated the value without the cyclic inputs
						// in mind, we need to check again, if the value of this component
						// should really be false. Maybe there is another reason for the whole
						// cycle to be true.
						if (!state.get(c.id)) {
							for (int inId : c.inputs) {
								if (state.get(inId)) {
									setComponentValue(c, state, true);
									break;
								}
							}
						}
						// else this component got changed to true, because some other
						// component in the cycle was setting its value to true
					}
				} else {
					// check with the remaining inputs if they are all false now
					value = false;
					for(int input:c.inputs) {
						if (state.get(input)) {
							value = true;
							break;
						}
					}
					if (!value) {
						setComponentValue(c, state, false);
					}
				}
			}
			break;
		case NOT:
			if (value == newInputValue) {
				setComponentValue(c, state, !newInputValue);
			}
			break;
		default:
			assert false;
			break;
		}
	}

	@Override
	public boolean isTerminal(InternalBitSetState state) {
		return state.get(propNetStructure.getTerminalProposition().id);
	}

	@Override
	protected void computeTransitions(InternalBitSetState state) {
		// nothing to do here
	}

	@Override
	protected void computeLegals(InternalBitSetState state, int roleId) {
		// nothing to do here
	}

	@Override
	protected void computeGoal(InternalBitSetState state, int roleId) {
		// nothing to do here
	}

	@Override
	protected void changeBaseOrInputValue(StaticComponent c, InternalBitSetState state, boolean newValue) {
		setComponentValue(c, state, newValue);
	}

}
