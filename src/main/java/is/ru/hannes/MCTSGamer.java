package is.ru.hannes;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

public final class MCTSGamer extends StateMachineGamer {

    MCTSNode root;
    MCTSNode currentNode;
    int i = 0;


    @Override
    public String getName() 
    {
        return "MCTSGamer";
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException 
    {
        long start = System.currentTimeMillis();
        //System.out.println(getStateMachine());
        root = new MCTSNode(getStateMachine(), getCurrentState(), null, null);
        currentNode = root;

        while (System.currentTimeMillis() - start < (timeout - 200))
        {
            runMCTS();
            Move bestActionForRole = root.getBestActionForRole(getRole());
            //System.out.println("current best is " + bestActionForRole);
        }

        Move bestActionForRole = root.getBestActionForRole(getRole());

        return bestActionForRole;
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

    public void runMCTS() throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        MCTSNode selectedNode = root.selection();
        List<Integer> playout = selectedNode.playout();
        selectedNode.parent.backprop(playout);
        if (i % 1000 == 0)
        {
            System.out.println("");
        }
        for (RoleMovePair rmp : root.roleMovePairToQ.keySet())
        {
            if (i % 1000 == 0)
            {
                System.out.println("action/Q for root: " + rmp.getMove() + ", " + root.roleMovePairToQ.get(rmp));
            }
        }
        i++;
    }

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Random gamer does no metagaming at the beginning of the match.
    }

    @Override
    public void stateMachineStop() 
    {
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
