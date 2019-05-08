package is.ru.hannes;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;

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

    HashMap<RoleMovePair, Double> roleMovePairToQ;
    HashMap<RoleMovePair, Integer> roleMovePairToN;

    MachineState state;
    StateMachine machine;
    
    public MCTSNode(StateMachine machine, MachineState state) 
    {
        this.machine = machine;
        this.state = state;
        N = 0;
        children = new LinkedList<MCTSNode>();
        roleMovePairToQ = new HashMap<RoleMovePair, Double>();
        roleMovePairToN = new HashMap<RoleMovePair, Integer>();
    }

    public void addChild(List<Move> jointMove) throws TransitionDefinitionException
    {
        children.add(new MCTSNode(machine, machine.getNextState(state, jointMove)));
    }

    public void expand() throws MoveDefinitionException, TransitionDefinitionException
    {
        List<List<Move>> legalJointMoves = machine.getLegalJointMoves(state);
        for (List<Move> jointMove : legalJointMoves)
        {
            addChild(jointMove);
            //addChild(new ArrayList<Move>(Arrays.asList(jointMove)));
        }
    }

    public void update(double r, RoleMovePair rmp)
    {
        N++;
        double currentValue = roleMovePairToQ.get(rmp);
        int currentN = roleMovePairToN.get(rmp);

        double nextValue = currentValue + ((r - currentValue)/(currentN + 1));

        // Q(s,r,a)
        roleMovePairToQ.put(rmp, nextValue);

        // N(s,r,a)
        roleMovePairToN.put(rmp, currentN + 1);
    }
}
