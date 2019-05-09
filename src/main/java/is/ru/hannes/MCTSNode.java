package is.ru.hannes;

import java.util.*;

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

    int N;

    HashMap<RoleMovePair, Integer> roleMovePairToQ;
    HashMap<RoleMovePair, Integer> roleMovePairToN;
    List<List<Move>> unexpandedJointMoves;

    MachineState state;
    StateMachine machine;
    MCTSNode parent;
    List<Move> prevAction;
    
    public MCTSNode(StateMachine machine, MachineState state, MCTSNode parent, List<Move> prevAction) throws MoveDefinitionException
    {
        this.machine = machine;
        this.state = state;
        this.parent = parent;
        this.prevAction = prevAction;
        N = 0;
        children = new LinkedList<MCTSNode>();
        roleMovePairToQ = new HashMap<>();
        roleMovePairToN = new HashMap<>();
        unexpandedJointMoves = machine.getLegalJointMoves(state);
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
        Move argmax = null;
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
        return argmax;
    }

    public MCTSNode selection() throws TransitionDefinitionException, MoveDefinitionException
    {
        // If we've reached a leaf
        if (!this.unexpandedJointMoves.isEmpty())
        {
            return this;
        }

        return getRandomChild().selection();
    }

    /*
    * Picks a random joint move from the legal moves, and creates a child
    * that results from doing that move from the current state.
    * Returns the newly created child node.
     */
    public MCTSNode expand() throws MoveDefinitionException, TransitionDefinitionException
    {
        if (this.machine.isTerminal(this.state))
        {
            return null;
        }
        Random rand = new Random();
        // Get a joint move that hasn't been used to expand, and expand it, remove it from the pool of moves
        int randomMoveIndex = rand.nextInt(unexpandedJointMoves.size());
        List<Move> randomJointMove = unexpandedJointMoves.get(randomMoveIndex);
        this.unexpandedJointMoves.remove(randomMoveIndex);
        MCTSNode child = createChild(randomJointMove);
        addChild(child);
        return child;
    }

    public List<Integer> playout() throws GoalDefinitionException
    {
        if (this.machine.isTerminal(this.state))
        {
            return this.machine.getGoals(this.state);
        }
        return getRandomChild().playout();
    }

    public void backprop(List<Integer> playoutGoals)
    {
        if (this.parent == null)
        {
            return;
        }

        for (RoleMovePair rmp : roleMovePairToQ.keySet())
        {
            // update(corresponding player's playout score, RoleMovePair for that player)
            update(playoutGoals.get(this.machine.getRoleIndices().get(rmp.getRole())), rmp);
        }
        this.parent.backprop(playoutGoals);
    }

    public MCTSNode getRandomChild()
    {
        Random rand = new Random();
        return this.children.get(rand.nextInt(this.children.size()));
    }

    public void update(Integer playout, RoleMovePair rmp)
    {
        N++;
        int currentRoleMoveN = roleMovePairToN.get(rmp);
        Integer currentRoleMoveQ = roleMovePairToQ.get(rmp);


        Integer nextValue = currentRoleMoveQ + ((playout - currentRoleMoveQ)/(currentRoleMoveN + 1));

        // Q(s,r,a)
        roleMovePairToQ.put(rmp, nextValue);

        // N(s,r,a)
        roleMovePairToN.put(rmp, currentRoleMoveN + 1);
    }
}
