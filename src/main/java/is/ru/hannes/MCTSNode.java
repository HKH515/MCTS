package is.ru.hannes;

import java.io.File;
import java.util.*;

import is.ru.cadia.ggp.propnet.bitsetstate.RecursiveForwardChangePropNetStateMachine;
import is.ru.cadia.ggp.propnet.statemachine.PropNetStateMachine;
import is.ru.cadia.ggp.propnet.structure.GGPBasePropNetStructureFactory;
import is.ru.cadia.ggp.utils.IOUtils;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class MCTSNode 
{
    List<MCTSNode> children;
    HashMap<List<Move>, MCTSNode> jointMovesToChildren;

    int N;

    HashMap<RoleMovePair, Integer> roleMovePairToQ;
    HashMap<RoleMovePair, Integer> roleMovePairToN;
    Set<List<Move>> unexpandedJointMoves;

    MachineState state;
    StateMachine machine;
    MCTSNode parent;
    List<Move> prevAction;

    public class TimeoutException extends Exception {}

    public MCTSNode(StateMachine machine, MachineState state, MCTSNode parent, List<Move> prevAction) throws MoveDefinitionException
    {
        this.machine = machine;
        this.state = state;
        this.parent = parent;
        this.prevAction = prevAction;
        this.children = new LinkedList<>();
        this.jointMovesToChildren = new HashMap<>();
        N = 0;
        roleMovePairToQ = new HashMap<>();
        roleMovePairToN = new HashMap<>();

        //unexpandedJointMoves = machine.getLegalJointMoves(state);
        unexpandedJointMoves = null;
    }

    private void addChild(MCTSNode node) throws TransitionDefinitionException, MoveDefinitionException
    {
        children.add(node);
    }

    private MCTSNode createChild(List<Move> jointMove) throws TransitionDefinitionException, MoveDefinitionException
    {
        return new MCTSNode(machine, machine.getNextState(state, jointMove), this, jointMove);
    }

    public Move getBestActionForRole(Role role)
    {
        List<Move> argmax = null;
        Integer qMax = Integer.MIN_VALUE;

        for (RoleMovePair rmp : roleMovePairToQ.keySet())
        {
            if (rmp.getRole().equals(role))
            {
                if (roleMovePairToQ.get(rmp) > qMax)
                {
                    qMax = roleMovePairToQ.get(rmp);
                    argmax = rmp.getMove();
                }
            }
        }

        int roleIdx = machine.getRoleIndices().get(role);

        return argmax.get(roleIdx);
    }

    public MCTSNode selection() throws TransitionDefinitionException, MoveDefinitionException
    {
        if (machine.isTerminal(state))
        {
            return this;
        }

        // if current node is unexpanded
        if (unexpandedJointMoves == null)
        {
            unexpandedJointMoves = new HashSet<>(machine.getLegalJointMoves(state));
        }

        List<Move> randomAction = getRandomJointAction();

        if (unexpandedJointMoves.contains(randomAction))
        {
            return expand(randomAction);
        }

        else
        {
            return getChild(randomAction);
        }
    }

    /*
    * Picks a random joint move from the legal moves, and creates a child
    * that results from doing that move from the current state.
    * Returns the newly created child node.
     */
    public MCTSNode expand(List<Move> randomJointMove) throws MoveDefinitionException, TransitionDefinitionException
    {
        if (machine.isTerminal(state))
        {
            return null;
        }

        // faster, but not sure if correct
        //this.unexpandedJointMoves.remove(randomMoveIndex);

        //slower, but correct
        unexpandedJointMoves.remove(randomJointMove);

        MCTSNode child = getChild(randomJointMove);
        initMapsForMove(randomJointMove);
        return child;
    }

    public List<Integer> playout(long timeout) throws TimeoutException, GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
    {
        long currentTime = System.currentTimeMillis();

        if (currentTime > timeout)
        {
            throw new TimeoutException();
        }

        if (machine.isTerminal(state))
        {
            return machine.getGoals(state);
        }

        Random rand = new Random();
        List<List<Move>> legalJointMoves = this.machine.getLegalJointMoves(state);
        int moveIndex = rand.nextInt(legalJointMoves.size());
        List<Move> chosenMove = legalJointMoves.get(moveIndex);
        MachineState nextState = machine.getNextState(state, chosenMove);
        return new MCTSNode(machine, nextState, this, chosenMove).playout(timeout);
    }

    public void backprop(List<Integer> playoutGoals, long timeout) throws TimeoutException
    {
        long currentTime = System.currentTimeMillis();

        if (currentTime > timeout)
        {
            throw new TimeoutException();
        }

        for (RoleMovePair rmp : roleMovePairToQ.keySet())
        {
            // update(corresponding player's playout score, RoleMovePair for that player)
            update(playoutGoals.get(machine.getRoleIndices().get(rmp.getRole())), rmp);
        }

        if (parent == null)
        {
            return;
        }

        parent.backprop(playoutGoals, timeout);
    }

    // The reason why we only do this for a certain move, is due to memory and time contraints, as we are
    // backpropping we only want to update the path up to the root, not the set of children belonging to
    // every node on the path.
    public void initMapsForMove(List<Move> move)
    {
        for (Role role : machine.getRoles())
        {
            RoleMovePair rmp = new RoleMovePair(role, move);
            //initialize q and n values to 0, if it does not exist already
            if (!roleMovePairToQ.containsKey(rmp))
            {
                roleMovePairToQ.put(rmp, 0);
            }
            if (!roleMovePairToN.containsKey(rmp))
            {
                roleMovePairToN.put(rmp, 0);
            }
        }

    }

    public List<Move> getRandomJointAction() throws MoveDefinitionException
    {
        Random rand = new Random();
        List<Move> chosenJointMove = machine.getRandomJointMove(state);
        return chosenJointMove;
    }

    public MCTSNode getRandomChild() throws MoveDefinitionException, TransitionDefinitionException
    {
        List<Move> jointMove = getRandomJointAction();
        MachineState nextState = machine.getNextState(state, jointMove);
        return new MCTSNode(machine, nextState, this, jointMove);
    }

    public MCTSNode getChild(List<Move> jointMove) throws TransitionDefinitionException, MoveDefinitionException
    {
        if (jointMovesToChildren.containsKey(jointMove))
        {
            return jointMovesToChildren.get(jointMove);
        }
        MCTSNode child = createChild(jointMove);
        addChild(child);
        jointMovesToChildren.put(jointMove, child);
        return child;
    }

    public void update(Integer playout, RoleMovePair rmp)
    {
        N++;
        Integer currentRoleMoveN = roleMovePairToN.get(rmp);
        Integer currentRoleMoveQ = roleMovePairToQ.get(rmp);


        Integer nextValue = currentRoleMoveQ + ((playout - currentRoleMoveQ)/(currentRoleMoveN + 1));

        // Q(s,r,a)
        roleMovePairToQ.put(rmp, nextValue);

        // N(s,r,a)
        roleMovePairToN.put(rmp, currentRoleMoveN + 1);
    }

    public static void main(String[] args) throws Exception
    {
        // setting up the state machine
        String gdlFileName = "/home/hannes/Documents/reasoner/games/games/ticTacToe/ticTacToe.kif";
        String gameDescription = IOUtils.readFile(new File(gdlFileName));
        String preprocessedRules = Game.preprocessRulesheet(gameDescription);
        Game ggpBaseGame = Game.createEphemeralGame(preprocessedRules);
        PropNetStateMachine stateMachine = new RecursiveForwardChangePropNetStateMachine(new GGPBasePropNetStructureFactory()); // insert your own machine here
        stateMachine.initialize(ggpBaseGame.getRules());

        //MCTSNode root = new MCTSNode();
    }
}
