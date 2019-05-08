package is.ru.cadia.ggp.propnet;

import is.ru.cadia.ggp.propnet.statemachine.PropNetStateMachine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;

/**
 * PropNetState wraps an InternalState into an object providing the MachineState interface
 * @author stephan
 *
 */
public class PropNetState<StateType extends InternalStateInterface> extends MachineState {

	public StateType internalState;
	public PropNetStateMachine<StateType> stateMachine;

	public PropNetState(StateType internalState, PropNetStateMachine<StateType> stateMachine) {
		super();
		this.internalState = internalState;
		this.stateMachine = stateMachine;
	}

	@Override
    public Set<GdlSentence> getContents()
    {
        return stateMachine.getSentences(internalState);
    }

	@SuppressWarnings("unchecked")
	@Override
	public PropNetState<StateType> clone() {
		return new PropNetState<StateType>((StateType)internalState.clone(), stateMachine);
	}

    @Override
    public String toString()
    {
        Set<GdlSentence> contents = getContents();
        if(contents == null)
            return "(MachineState with null contents)";
        else {
        	List<GdlSentence> l = new ArrayList<>(contents);
        	Collections.sort(l, new Comparator<GdlSentence>() {
				@Override
				public int compare(GdlSentence o1, GdlSentence o2) {
					return o1.toString().compareTo(o2.toString());
				}
			});
            return "(" + l.toString() + ")";
        }
    }

	public StateType getInternalState() {
		return internalState;
	}


}
