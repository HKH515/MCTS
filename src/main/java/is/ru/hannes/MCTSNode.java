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
    private HashMap<List<Move>, MCTSNode> jointMovesToChildren;

    private int N;

    private HashMap<RoleMovePair, Integer> roleMovePairToQ;
    private HashMap<RoleMovePair, Integer> roleMovePairToN;
    private HashMap<RoleMovePair, QNPair> QMAST;
    private HashMap<RoleMovePair, double[]> roleMovePairToFeature;
    private Set<List<Move>> unexpandedJointMoves;

    private MachineState state;
    private StateMachine machine;
    private ApprenticePolicy apprentice;
    MCTSNode parent;
    private List<Move> prevAction;
    private boolean useQMAST;

    class TimeoutException extends Exception {}

    MCTSNode(StateMachine machine, MachineState state, MCTSNode parent, List<Move> prevAction,
        HashMap<RoleMovePair, QNPair> QMAST, boolean useQMAST, ApprenticePolicy apprentice)
    {
        this.machine = machine;
        this.state = state;
        this.parent = parent;
        this.prevAction = prevAction;
        this.children = new LinkedList<>();
        this.jointMovesToChildren = new HashMap<>();
        this.roleMovePairToFeature = new HashMap<>();
        this.QMAST = QMAST;
        this.useQMAST = useQMAST;
        N = 0;
        roleMovePairToQ = new HashMap<>();
        roleMovePairToN = new HashMap<>();

        //unexpandedJointMoves = machine.getLegalJointMoves(state);
        unexpandedJointMoves = null;
        this.apprentice = apprentice;
    }

    HashMap<RoleMovePair, double[]> getCachedFeature()
    {
        return roleMovePairToFeature;
    }

    Double getActionProbability(List<Move> action, Role role)
    {   
        RoleMovePair rmp = new RoleMovePair(role, action);

        Integer visits = roleMovePairToN.get(rmp);

        if (visits == null)
        {
            return 0.0;
        }
        
        return visits / Double.valueOf(N);
    }
    
    private void addChild(MCTSNode node) throws TransitionDefinitionException, MoveDefinitionException
    {
        children.add(node);
    }

    private MCTSNode createChild(List<Move> jointMove) throws TransitionDefinitionException, MoveDefinitionException
    {
        return new MCTSNode(machine, machine.getNextState(state, jointMove), this, jointMove, QMAST, useQMAST, apprentice);
    }

    List<Move> getPrevAction()
    {
        return prevAction;
    }

    // finds a joint move that maximizes the score for a specific role.
    // returns a list of moves, an example is [noop, noop, noop, move a 3 b 4, noop, noop]
    List<Move> getBestActionForRole(Role role, SelectionHeuristic heuristic, double explorationFactor) throws MoveDefinitionException
    {
        List<Move> argmax = null;
        double qMax = Double.NEGATIVE_INFINITY;

        List<List<Move>> legalJointMoves = machine.getLegalJointMoves(state);

        for (List<Move> move : legalJointMoves)
        {
            // make sure we have an entry in the map
            initMapsForMove(move);
            RoleMovePair rmp = new RoleMovePair(role, move);

            if (rmp.getRole().equals(role))
            {
                if (heuristic.equals(SelectionHeuristic.UCB))
                {
                    double ucbValue = ucbHeuristic(roleMovePairToQ.get(rmp), roleMovePairToN.get(rmp), N, explorationFactor);
                    if (ucbValue > qMax)
                    {
                        qMax = ucbValue;
                        argmax = rmp.getMove();
                    }
                }

                if (heuristic.equals(SelectionHeuristic.BiasedUCB))
                {
                    // We need all the feature vectors here..
                    List<double[]> featureVectors = new ArrayList<>();
                    for (List<Move> m : legalJointMoves)
                    {
                        RoleMovePair roleMovePair = new RoleMovePair(role, m);

                        // check for cached feature vector and otherwise make apprentice compute it
                        double[] featureVector = roleMovePairToFeature.get(roleMovePair);
                        if (featureVector == null)
                        {
                            featureVector = apprentice.computeFeatureVector(getState(), move, role);
                            featureVectors.add(featureVector);
                            roleMovePairToFeature.put(roleMovePair, featureVector);
                        }
                    }

                    // then compute p with the feature vectors
                    double[] p = apprentice.computeProbabilities(legalJointMoves, featureVectors);

                    double ucbValue = biasedUCBHeuristic(roleMovePairToQ.get(rmp), roleMovePairToN.get(rmp), N, explorationFactor, p[legalJointMoves.indexOf(move)]);

                    if (ucbValue > qMax)
                    {
                        qMax = ucbValue;
                        argmax = rmp.getMove();
                    }
                }
            }
        }

        return argmax;
    }

    // Rather than e.g. returning [noop, noop, noop, move a 3 b 4, noop, noop],
    // will only give move a 3 b 4 if the 5th role is requested
    public Move getBestNonJointActionForRole(Role role, SelectionHeuristic heuristic, double explorationFactor) throws MoveDefinitionException
    {
        int roleIdx = machine.getRoleIndices().get(role);
        return getBestActionForRole(role, heuristic, explorationFactor).get(roleIdx);
    }


    private double ucbHeuristic(int qValue, int nValue, int nOfNode, double c)
    {
        if (nValue == 0)
        {
            return Integer.MAX_VALUE;
        }
        //System.out.println("Math.sqrt(Math.log(" + nOfNode + ")/" + nValue + ")))" + " = " + Math.sqrt(Math.log(nOfNode)/nValue));
        return qValue + (c * Math.sqrt(Math.log(nOfNode)/nValue));
    }

    private double biasedUCBHeuristic(int qValue, int nValue, int nOfNode, double c, double p)
    {
        if (nValue == 0)
        {
            return Integer.MAX_VALUE;
        }

        return qValue + (c * p * Math.sqrt(Math.log(nOfNode)/nValue));
    }

    public MCTSNode selection(Role role, SelectionHeuristic heuristic, double explorationFactor, boolean shouldExpand) throws TransitionDefinitionException, MoveDefinitionException
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

        List<Move> action = getBestActionForRole(role, heuristic, explorationFactor);


        if (shouldExpand)
        {
            if (unexpandedJointMoves.contains(action))
            {
                return expand(action);
            }

            else
            {
                return getChild(action).selection(role, heuristic, explorationFactor, shouldExpand);
            }
        }
        else
        {
            // if we've run out of memory, we return the current node as the selected
            return this;
        }
    }


    /*
    * Picks a random joint move from the legal moves, and creates a child
    * that results from doing that move from the current state.
    * Returns the newly created child node.
     */
    private MCTSNode expand(List<Move> randomJointMove) throws MoveDefinitionException, TransitionDefinitionException
    {
        assert (randomJointMove != null);
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

    List<Integer> playout(long timeout, Role roleToMaximize) throws TimeoutException, GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException
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
        int moveIndex;
        if (useQMAST)
        {
            List<Move> maxGibbsMove = getMostLikelyMoveGibbs(legalJointMoves, roleToMaximize);
            moveIndex = legalJointMoves.indexOf(maxGibbsMove);
        }
        else
        {
            moveIndex = rand.nextInt(legalJointMoves.size());
        }
        List<Move> chosenMove = legalJointMoves.get(moveIndex);
        MachineState nextState = machine.getNextState(state, chosenMove);
        return new MCTSNode(machine, nextState, this, chosenMove, QMAST, useQMAST, apprentice).playout(timeout, roleToMaximize);
    }

    void backprop(List<Integer> playoutGoals, List<Move> playerMoves, long timeout) throws TimeoutException
    {
        long currentTime = System.currentTimeMillis();

        if (currentTime > timeout)
        {
            throw new TimeoutException();
        }

        for (RoleMovePair rmp : roleMovePairToQ.keySet())
        {
            // update(corresponding player's playout score, RoleMovePair for that player)
            if (rmp.getMove().equals(playerMoves)) 
            {
                update(playoutGoals.get(machine.getRoleIndices().get(rmp.getRole())), rmp);
            }
        }

        if (parent == null)
        {
            return;
        }

        parent.backprop(playoutGoals, this.prevAction, timeout);
    }

    // The reason why we only do this for a certain move, is due to memory and time contraints, as we are
    // backpropping we only want to update the path up to the root, not the set of children belonging to
    // every node on the path.
    private void initMapsForMove(List<Move> move)
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

    private List<Move> getRandomJointAction() throws MoveDefinitionException
    {
        return machine.getRandomJointMove(state);
    }

    public MCTSNode getRandomChild() throws MoveDefinitionException, TransitionDefinitionException
    {
        List<Move> jointMove = getRandomJointAction();
        MachineState nextState = machine.getNextState(state, jointMove);
        return new MCTSNode(machine, nextState, this, jointMove, QMAST, useQMAST, apprentice);
    }

    private MCTSNode getChild(List<Move> jointMove) throws TransitionDefinitionException, MoveDefinitionException
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

        if (useQMAST)
        {
            // Update QMAST values
            QNPair currentQMAST = QMAST.get(rmp);

            if (currentQMAST == null)
            {
                QMAST.put(rmp, new QNPair());
                currentQMAST = QMAST.get(rmp);
            }

            Integer qmastQ = currentQMAST.getQ();
            Integer qmastN = currentQMAST.getN();

            nextValue = qmastQ + ((playout - qmastQ)/(++qmastN));
            
            currentQMAST.setQ(nextValue);
            currentQMAST.setN(qmastN);
        }
    }

    private List<Move> getMostLikelyMoveGibbs(List<List<Move>> moves, Role roleToMaximize)
    {
        List<Move> argMax = null;
        double max = Double.NEGATIVE_INFINITY;

        for (List<Move> move : moves)
        {
            double gibbsScore = gibbs(moves, move, roleToMaximize, 0.5);
            if (gibbsScore > max)
            {
                argMax = move;
                max = gibbsScore;
            }
        }
        return argMax;

    }

    public MachineState getState()
    {
        return state;
    }

    double gibbs(List<List<Move>> moves, List<Move> a, Role roleToMaximize, double tau)
    {
        double denom = 0;
        for (List<Move> move : moves)
        {
            RoleMovePair rmp = new RoleMovePair(roleToMaximize, move);
            if (QMAST.containsKey(rmp))
            {
                denom += Math.exp(QMAST.get(rmp).getQ()/tau);
            }
        }
        // If we're going to be dividing by 0, we return infinity
        if (denom == 0)
        {
            return Double.POSITIVE_INFINITY;
        }
        RoleMovePair rmp = new RoleMovePair(roleToMaximize, a);
        // If the denominator is not found, we treat it as infinity (limit of e^infty = infty)
        if (!QMAST.containsKey(rmp))
        {
            return Double.POSITIVE_INFINITY;
        }
        return Math.exp(QMAST.get(rmp).getQ()/tau)/denom;
    }



    public static void main(String[] args) throws Exception
    {

    }
}
