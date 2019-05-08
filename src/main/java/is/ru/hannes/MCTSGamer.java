package is.ru.hannes;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import is.ru.hannes.CustomPropNetStateMachine;
import is.ru.hannes.CustomMachineState;

public final class MCTSGamer extends StateMachineGamer {

    MCTSNode root;


    public MCTSGamer() {
       super();
       root = new MCTSNode();
    }

    public MCTSNode selection() {
        // TODO: sort the leafs of the tree according to some comparator (heuristic)

        return null;
    }

    public void expand(MCTSNode node) throws MoveDefinitionException 
    {
        List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
    }

    @Override
    public String getName() {
        return "MCTSGamer";
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        return null;
    }

    @Override
    public StateMachine getInitialStateMachine() {
        //return new CachedStateMachine(new ProverStateMachine());
        return new CustomPropNetStateMachine();
    }

    @Override
    public void preview(Game g, long timeout) throws GamePreviewException {
        // Random gamer does no game previewing.
    }

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Random gamer does no metagaming at the beginning of the match.
    }

    @Override
    public void stateMachineStop() {
        // Random gamer does no special cleanup when the match ends normally.
    }

    @Override
    public void stateMachineAbort() {
        // Random gamer does no special cleanup when the match ends abruptly.
    }

    @Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }
}
