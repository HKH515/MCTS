package is.ru.hannes;

import java.util.List;

import is.ru.hannes.MCTSNode.TimeoutException;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import is.ru.cadia.ggp.propnet.bitsetstate.RecursiveForwardChangePropNetStateMachine;
import is.ru.cadia.ggp.propnet.structure.GGPBasePropNetStructureFactory;

import org.ggp.base.util.statemachine.Move;

public final class BiasedMCTSGamer extends StateMachineGamer
{
    private MCTSNode root;
    private MCTSNode currentNode;
    private double explorationFactor = 50;
    private SelectionHeuristic heuristic = SelectionHeuristic.BiasedUCB;
    private ApprenticePolicy apprentice;


    @Override
    public String getName()
    {
        return "BiasedMCTSGamer";
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if (apprentice == null)
        {
            apprentice = new ApprenticePolicy(getStateMachine(), 4, getMatch().getGame().getRules());
        }

        long end = timeout - 50;

        {
            root = new MCTSNode(getStateMachine(), getCurrentState(), null, null, null, false, apprentice);
            currentNode = root;
        }

        List<Move> bestActionForRole;

        int iteration = 0;

        while (System.currentTimeMillis() < end)
        {
            runMCTS(end);
            if (iteration++ % 20 == 0)
            {
                apprentice.doGradientDescentUpdate(currentNode, getRole(), currentNode.getCachedFeature());
            }
        }

        bestActionForRole = currentNode.getBestActionForRole(getRole(), heuristic, explorationFactor);
        apprentice.breakthroughFeatureVectorForStateActionPair(getCurrentState(), bestActionForRole, getRole());
        return bestActionForRole.get(getStateMachine().getRoleIndices().get(getRole()));
    }

    @Override
    public StateMachine getInitialStateMachine()
    {
        return new RecursiveForwardChangePropNetStateMachine(new GGPBasePropNetStructureFactory());
    }

    @Override
    public void preview(Game g, long timeout)
    {
        // Random gamer does no game previewing.
    }


    private int depth(MCTSNode node)
    {
        if (node.children.isEmpty())
        {
            return 0;
        }
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

    private void runMCTS(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        try
        {
            MCTSNode selectedNode = currentNode.selection(getRole(), heuristic, explorationFactor, (depth(root) < 230));
            List<Integer> playout = selectedNode.playout(timeout, getRole());
            selectedNode.parent.backprop(playout, selectedNode.getPrevAction(), timeout);

        }
        catch (TimeoutException e)
        {
            // Nothing to do here
            System.out.println("Deliberation time is up - going with current best");
        }
    }

    @Override
    public void stateMachineMetaGame(long timeout)
    {
        //runMCTS(timeout);
        // Random gamer does no metagaming at the beginning of the match.
    }

    @Override
    public void stateMachineStop()
    {
        root = null;
        currentNode = null;
        apprentice = null;
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
