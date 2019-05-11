package is.ru.hannes;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import is.ru.hannes.MCTSNode.TimeoutException;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import is.ru.hannes.CustomPropNetStateMachine;
import is.ru.hannes.CustomMachineState;
import is.ru.cadia.ggp.propnet.bitsetstate.RecursiveForwardChangePropNetStateMachine;
import is.ru.cadia.ggp.propnet.structure.GGPBasePropNetStructureFactory;

import java.util.HashMap;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

public final class MCTSGamer extends StateMachineGamer 
{

    MCTSNode root;
    MCTSNode currentNode;
    double explorationFactor = 50;
    SelectionHeuristic heuristic = SelectionHeuristic.UCB;
    
    int i = 0;


    @Override
    public String getName() 
    {
        return "MCTSGamer";
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException 
    {
        long end = timeout ;
        //System.out.println(getStateMachine());
        if (currentNode == null)
        {
            root = new MCTSNode(getStateMachine(), getCurrentState(), null, null);
            currentNode = root;
        }

        List<Move> bestActionForRole = null;
        while (System.currentTimeMillis() < end)
        {
            runMCTS(end);
            bestActionForRole = currentNode.getBestActionForRole(getRole(), heuristic, explorationFactor);
        }

        bestActionForRole = currentNode.getBestActionForRole(getRole(), heuristic, explorationFactor);
        Move nonJointBestActionForRole = bestActionForRole.get(getStateMachine().getRoleIndices().get(getRole()));

        currentNode = root.getChild(bestActionForRole);
        return nonJointBestActionForRole;
    }

    @Override
    public StateMachine getInitialStateMachine() 
    {
        return new RecursiveForwardChangePropNetStateMachine(new GGPBasePropNetStructureFactory());
    }

    @Override
    public void preview(Game g, long timeout) throws GamePreviewException 
    {
        // Random gamer does no game previewing.
    }

    public int depth(MCTSNode node)
    {
        if (node.children.isEmpty())
        {
            return 0;
        }
        MCTSNode argmax = null;
        int max = Integer.MIN_VALUE;
        for (MCTSNode child : node.children)
        {
            int depth = 1 + depth(child);
            if (depth > max)
            {
                max = depth;
            }
        }
        return max;
    }

    public void runMCTS(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        try 
        {
            MCTSNode selectedNode = currentNode.selection(getRole(), heuristic, explorationFactor);
            List<Integer> playout = selectedNode.playout(timeout);
            selectedNode.parent.backprop(playout, timeout);
            //selectedNode.backprop(playout, timeout);


             for (RoleMovePair rmp : currentNode.roleMovePairToQ.keySet())
             {
                 if (i % 1000 == 0 && rmp.getRole().equals(getRole()))
                 {
                     System.out.println("action/Q for root: " + rmp.getMove() + " for role " + rmp.getRole() + " for root: " + currentNode.roleMovePairToQ.get(rmp));
                    }
                }
            if (i % 1000 == 0)
            {
                System.out.println("Tree depth: " + depth(currentNode));
                System.out.println();

            }
                i++;

        } 
        catch (TimeoutException e)
        {
            // Nothing to do here
            System.out.println("Timeout - going with current best");
        }
    }

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Random gamer does no metagaming at the beginning of the match.
    }

    @Override
    public void stateMachineStop() 
    {
        root = null;
        currentNode = null;
        // Random gamer does no special cleanup when the match ends normally.
    }

    @Override
    public void stateMachineAbort() 
    {
        // Random gamer does no special cleanup when the match ends abruptly.
    }

    @Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }

    public static void main(String[] args) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        MCTSGamer gamer = new MCTSGamer();
        Move move = gamer.stateMachineSelectMove(1000);
    }
}
