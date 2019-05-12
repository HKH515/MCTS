package is.ru.hannes;

public class ShallowTreeException extends Exception {
    public String toString()
    {
        return "It seems as if the MCTS tree is too shallow to do backprop, the tree is most likely just a single node.";
    }
}
