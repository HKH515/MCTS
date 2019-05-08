package is.ru.cadia.ggp.propnet.statemachine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import is.ru.cadia.ggp.propnet.InternalStateInterface;
import is.ru.cadia.ggp.propnet.PropNetMove;
import is.ru.cadia.ggp.propnet.PropNetState;
import is.ru.cadia.ggp.propnet.structure.PropNetStructure;
import is.ru.cadia.ggp.propnet.structure.PropNetStructureFactory;
import is.ru.cadia.ggp.propnet.structure.components.BaseProposition;
import is.ru.cadia.ggp.propnet.structure.components.StaticComponent;

public abstract class PropNetStateMachine<StateType extends InternalStateInterface> extends StateMachine {

	/**
     * the initial state of the game
     */
	private StateType initialState;

    /**
     * the base state of the game, where everything is false
     */
	private StateType baseState;

	/** The underlying proposition network  */
	protected PropNetStructure propNetStructure;
	/** The player roles */
	protected List<Role> roles;
	/** the number of roles in the game */
	protected int nbRoles;

	/**
	 * all input components
	 */
	protected BitSet inputComponentSet;

	/**
	 * the factory to create the PropNetStructure underlying this state machine
	 */
	protected PropNetStructureFactory factory;

	private Random random = new Random();

	private static <X> void crossProduct(List<List<X>> listOfLists, List<X[]> crossProduct,	ArrayList<X> partial, X[] pattern) {
	    if (partial.size() == listOfLists.size()) {
	        crossProduct.add(partial.toArray(pattern));
	    } else {
	        for (X item : listOfLists.get(partial.size())) {
	            partial.add(item);
	            crossProduct(listOfLists, crossProduct, partial, pattern);
	            partial.remove(partial.size()-1);
	        }
	    }
	}

	/**
	 * initialize the state machine from an existing PropnetStructure
	 * @param propNetStructure
	 */
	public void initialize(PropNetStructure propNetStructure) {
		this.propNetStructure = propNetStructure;
		propNetStructure.printStats();

		// for debugging
		// propNetStructure.renderToFile(new File("propnet.dot"));

		// setup roles
	    roles = Arrays.asList(propNetStructure.getRoles());
	    nbRoles = roles.size();

		inputComponentSet = new BitSet();
		for(int roleId = 0; roleId < nbRoles; roleId++) {
			PropNetMove[] moves = propNetStructure.getPossibleMoves(roleId);
			for (PropNetMove move : moves) {
				inputComponentSet.set(move.getInputComponent().id);
			}
		}

	    baseState = makeBaseState();
	    initialState = makeInitialState();
		System.out.println("initial state: " + internalStateToMachineState(initialState));
	}

	protected final StateType makeInitialState() {
	StateType state = getAnEmptyState();
		for(BaseProposition p : propNetStructure.getBasePropositions()) {
			if (p.initialValue) {
				changeBaseOrInputValue(p, state, true);
			}
		}
		return state;
	}

	/**
	 *
	 * @return a state in which no fluent is true, but that is otherwise consistent
	 */
	protected abstract StateType makeBaseState();

	/**
	 * initialize the PropNetStateMachine from the game rules
	 *
	 * This function must not be used if the PropNetStateMachine was constructed using a PropNetStructure.
	 */
	@Override
	public final void initialize(List<Gdl> description) {
	    try {
	    	if (factory != null) {
		    	initialize(factory.create(description));
		    	factory.cleanup();
		    } else {
		    	throw new RuntimeException("not possible to initialize this PropNetStateMachine from game rules without a factory for a PropNetStructure");
		    }
	    } catch (InterruptedException e) {
	        throw new RuntimeException(e);
		}
	}

	@Override
	public int getGoal(MachineState state, Role role) throws GoalDefinitionException {
				return getGoal(machineStateToInternalState(state), propNetStructure.getRoleId(role));
			}

	public int getGoal(StateType state, int roleId) throws GoalDefinitionException {
		computeGoal(state, roleId);
		StaticComponent[] goalProps = propNetStructure.getGoalPropositions(roleId);
		int[] goalValues = propNetStructure.getGoalValues(roleId);
		for (int i = 0; i<goalValues.length; i++) {
			if (state.get(goalProps[i].id)) {
				return goalValues[i];
			}
		}
		throw new GoalDefinitionException(internalStateToMachineState(state), this.getRoles().get(roleId));
	}

	protected abstract void computeGoal(StateType state, int roleId);

	public final List<Integer> getGoals(StateType state) throws GoalDefinitionException {
		ArrayList<Integer> goals = new ArrayList<>(nbRoles);
		for (int roleId = 0; roleId < nbRoles ; roleId++) {
			goals.add(getGoal(state, roleId));
		}
		return goals;
	}

	/**
	 *
	 * @param state
	 * @return the internal propnet state for the given MachineState
	 */
	@SuppressWarnings("unchecked")
	protected final StateType machineStateToInternalState(MachineState state) {
		if (state instanceof PropNetState && ((PropNetState<?>)state).stateMachine == this) {
			return ((PropNetState<StateType>)state).internalState;
		}
		Set<GdlSentence> sentences = state.getContents();
		int[] bases = new int[sentences.size()];
		int i=0;
		for (GdlSentence sentence : sentences) {
			BaseProposition p = propNetStructure.getBaseProposition(sentence);
			if (p != null) {
				bases[i] = p.id;
				i++;
			} else {
					System.out.println("no base proposition exists for " + sentence);
			}
		}
		if (i<bases.length) {
			bases = Arrays.copyOf(bases, i);
		}
		return getStateForBases(bases);
	}

	/**
	 * returns an internal state with the given base components set to true (and other base components set to false)
	 * @param bases IDs of base components that are true in the state
	 * @return
	 */
	protected final StateType getStateForBases(int[] bases) {
		StateType state = getAnEmptyState();
		for (int id : bases) {
			changeBaseOrInputValue(propNetStructure.getComponent(id), state, true);
		}
		return state;
	}

	@Override
	public final boolean isTerminal(MachineState state) {
		return isTerminal(machineStateToInternalState(state));
	}

	public abstract boolean isTerminal(StateType machineStateToInternalState);

	@Override
	public List<Role> getRoles() {
		return roles;
	}

	@Override
	public final MachineState getInitialState() {
		return internalStateToMachineState(getInternalInitialState());
	}

	public final MachineState internalStateToMachineState(StateType internalState) {
		return new PropNetState<StateType>(internalState, this);
	}

	public final StateType getInternalInitialState() {
		return initialState;
	}

	/**
	 * actually returns a list of PropNetMove objects, to make it easier to map back to input propositions
	 */
	@Override
	public final List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException {
		return getLegalMoves(machineStateToInternalState(state), propNetStructure.getRoleId(role));
	}

	/**
	 * this default implementation calls computeLegals(state,roleId) and then queries the values
	 * of the legal components of the state to compose a list legal moves
	 * @param state
	 * @param roleId
	 * @return
	 * @throws MoveDefinitionException
	 */
	public final List<Move> getLegalMoves(StateType state, int roleId) throws MoveDefinitionException {
		computeLegals(state, roleId);
		PropNetMove[] possibleMoves = propNetStructure.getPossibleMoves(roleId);
		int nbLegals = 0;
		for (PropNetMove m : possibleMoves) {
			if (state.get(m.getLegalComponent().id)) {
				nbLegals++;
			}
		}
		if (nbLegals == 0) {
			throw new MoveDefinitionException(internalStateToMachineState(state), roles.get(roleId));
		}
		List<Move> moves = new ArrayList<Move>(nbLegals);
		for (PropNetMove m : possibleMoves) {
			if (state.get(m.getLegalComponent().id)) {
				moves.add(m);
			}
		}
		return moves;
	}

	/**
	 * makes sure that all the legal(R,M) components for role R (with id roleId) are computed/up-to-date in state
	 * @param state
	 * @param roleId
	 */
	protected abstract void computeLegals(StateType state, int roleId);

	public final PropNetMove[] getRandomJointMove(StateType state) throws MoveDefinitionException {
		PropNetMove[] jointMove = new PropNetMove[nbRoles];
		for (int roleId = 0; roleId < nbRoles; roleId++) {
			jointMove[roleId] = getRandomMove(state, roleId);
		}
		return jointMove;
	}

	public PropNetMove getRandomMove(StateType state, int roleId) throws MoveDefinitionException {
//		List<Move> moves = getLegalMoves(state, roleId);
//		return (PropNetMove)moves.get(random.nextInt(moves.size()));

		// the implementation here iterates over the possible moves twice,
		// but has the advantage of not allocating a list of legal moves
		computeLegals(state, roleId);
		PropNetMove[] moves = propNetStructure.getPossibleMoves(roleId);
		int nbLegals = 0;
		for (PropNetMove m : moves) {
			if (state.get(m.getLegalComponent().id)) {
				nbLegals++;
			}
		}
		if (nbLegals == 0) {
			throw new MoveDefinitionException(internalStateToMachineState(state), roles.get(roleId));
		}
		int moveIdx = random.nextInt(nbLegals);
		for (PropNetMove m : moves) {
			if (state.get(m.getLegalComponent().id)) {
				if (moveIdx == 0) {
					return m;
				}
				moveIdx--;
			}
		}
		return null;
	}

	@Override
	public final MachineState getNextState(MachineState state, List<Move> moves) throws TransitionDefinitionException {
		StateType nextInternalState = getNextState(machineStateToInternalState(state), movesToInternalMoves(moves), true);
		return internalStateToMachineState(nextInternalState);
	}

	@SuppressWarnings("unchecked")
	public StateType getNextState(StateType state, PropNetMove[] jointMove, boolean makeNewState) {
		// set joint move in the state and reset the relevant computed bits
		setInputs(state, jointMove);

		// compute the transitions
		computeTransitions(state);

		// set bases in the next state to transitions in the previous one
		// marking the components whose values might have changed as !computed
		StateType nextState;
		if (makeNewState)
			nextState = (StateType) state.clone();
		else
			nextState = state;

		BaseProposition[] baseProps = propNetStructure.getBasePropositions();
		boolean[] newBaseValues = new boolean[baseProps.length];
		// do this in two steps here, in case a base proposition is also a next component somewhere else
		for (int i = 0; i < baseProps.length; i++) {
			newBaseValues[i] = state.get(baseProps[i].nextComponent.id);
		}
		for (int i = 0; i < baseProps.length; i++) {
			if (nextState.get(i) != newBaseValues[i]) {
//				System.out.println("change base " + baseProps[i].sentences[0] + " to " + newBaseValues[i]);
				changeBaseOrInputValue(baseProps[i], nextState, newBaseValues[i]);
			}
		}
		return nextState;
	}

	protected void setInputs(StateType state, PropNetMove[] jointMove) {
		BitSet bitsToUnSet = (BitSet)inputComponentSet.clone();
		for (PropNetMove move : jointMove) {
			int inputId = move.getInputComponent().id;
			bitsToUnSet.clear(inputId);
			if (!state.get(inputId)) {
				changeBaseOrInputValue(move.getInputComponent(), state, true);
//				System.out.println("set input to true:" + move.getInputComponent().id + " - " + move);
			// } else { // nothing needs to change
			}
		}
		for (int nextId = bitsToUnSet.nextSetBit(0); nextId != -1; nextId = bitsToUnSet.nextSetBit(nextId+1)) {
			if (state.get(nextId)) {
//				System.out.println("set input to false:" + nextId);
				changeBaseOrInputValue(propNetStructure.getComponent(nextId), state, false);
			}
		}
	}

	/**
	 * computes the values of the transition components
	 * @param state
	 */
	protected abstract void computeTransitions(StateType state);

	/**
	 * changes value of c in state to newValue ensuring consistency
	 * must only be called when c's current value in state != newValue and
	 * only for components c that are either BASE or INPUT type.
	 * @param c
	 * @param state
	 * @param newValue
	 */
	protected abstract void changeBaseOrInputValue(StaticComponent c, StateType state, boolean newValue);

	@Override
	public final MachineState getNextStateDestructively(MachineState state, List<Move> moves)	throws TransitionDefinitionException {
		if (state instanceof PropNetState && ((PropNetState<?>) state).stateMachine == this){
			@SuppressWarnings("unchecked")
			PropNetState<StateType> pState = (PropNetState<StateType>)state;
			getNextState(pState.internalState, movesToInternalMoves(moves), false);
			return state;
		} else {
			return getNextState(state, moves);
		}
	}

	/**
	 * convert a joint move (one move per role) into the IDs of the associated input propositions
	 * @param moves
	 * @return array of IDs of input propositions
	 */
	protected final PropNetMove[] movesToInternalMoves(List<Move> moves) {
		assert moves.size()==roles.size();
		PropNetMove[] jointMove = new PropNetMove[moves.size()];
		int rid = 0;
		for (Move m : moves) {
			if (m instanceof PropNetMove) {
				jointMove[rid] = ((PropNetMove)m);
			} else {
				jointMove[rid] = propNetStructure.getPropNetMove(rid, m);
			}
//			if(jointMove[rid] == null) {
//				throw new RuntimeException("no input prop for " + m + " class(m): " + m.getClass());
//			}
			rid++;
		}
		return jointMove;
	}

	public PropNetStructure getPropNetStructure() {
		return propNetStructure;
	}

	@Override
	public final List<Move> getRandomJointMove(MachineState machineState) throws MoveDefinitionException {
		StateType state = machineStateToInternalState(machineState);
		List<Move> jointMove = new ArrayList<>(nbRoles);
		for (int roleId = 0; roleId < nbRoles; roleId++) {
			jointMove.add(getRandomMove(state, roleId));
		}
		return jointMove;
	}

	@Override
	public final List<Move> getRandomJointMove(MachineState machineState, Role role, Move move) throws MoveDefinitionException {
		StateType state = machineStateToInternalState(machineState);
		int roleIdForRole = propNetStructure.getRoleId(role);
		List<Move> jointMove = new ArrayList<>(nbRoles);
		for (int roleId = 0; roleId < nbRoles; roleId++) {
			if (roleIdForRole == roleId) {
				jointMove.add(move);
			} else {
				jointMove.add(getRandomMove(state, roleId));
			}
		}
		return jointMove;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final List<PropNetMove[]> getLegalJointMoves(StateType state) throws MoveDefinitionException {
	    List<List<PropNetMove>> legals = new ArrayList<List<PropNetMove>>();
	    for (int roleId = 0; roleId < nbRoles; roleId++) {
	        legals.add((List)getLegalMoves(state, roleId));
	    }

	    List<PropNetMove[]> crossProduct = new ArrayList<PropNetMove[]>();
	    crossProduct(legals, crossProduct, new ArrayList<PropNetMove>(nbRoles), new PropNetMove[]{});

	    return crossProduct;
	}

	public PropNetStateMachine(PropNetStructureFactory factory) {
		super();
		this.factory = factory;
	}

	public PropNetStateMachine(PropNetStructure propNetStructure) {
		super();
		initialize(propNetStructure);
	}

	public final Set<GdlSentence> getSentences(StateType state) {
		Set<GdlSentence> sentences = new HashSet<>();
		for (BaseProposition p : propNetStructure.getBasePropositions()) {
			if (state.get(p.id)) {
				sentences.addAll(Arrays.asList(p.sentences));
			}
		}
		return sentences;
	}

	@SuppressWarnings("unchecked")
	public final StateType getAnEmptyState() {
		return (StateType)baseState.clone();
	}

//	public void debugPrint(StateType state) {
//		for (BaseProposition p : propNetStructure.getBasePropositions()) {
//			if (state.get(p.id)) {
//				System.out.print(p.id + "-" + p.sentences[0] + ", ");
//			}
//		}
//		System.out.println();
//	}

}