import java.util.*;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        this.utxoPool = new UTXOPool(utxoPool);
    }

    public UTXOPool getUTXOPool() {
        return utxoPool;
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        HashSet<UTXO> usedUTXOs = new HashSet<UTXO>();
        double inputValue = 0.0;
        double outputValue = 0.0;

        // Verify all used inputs are in UTXO pool and no UTXO is used more than once and signautures are valid
        for(int i=0; i<inputs.size(); i++)
        {
            Transaction.Input input = inputs.get(i);
            UTXO currCandidate = new UTXO(input.prevTxHash, input.outputIndex);
            if (utxoPool.contains(currCandidate) == false || usedUTXOs.contains(currCandidate))
                return false;
            usedUTXOs.add(currCandidate);

            // verify input signature
            Transaction.Output unspentTxOutput = utxoPool.getTxOutput(currCandidate);
            byte[] signature = input.signature;
            byte[] rawData = tx.getRawDataToSign(i);

            if (Crypto.verifySignature(unspentTxOutput.address, rawData, signature) == false)
                return false;

            inputValue += unspentTxOutput.value;
        }
        
        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        for(Transaction.Output ot : outputs)
        {
            if (ot.value < 0)
                return false;
            outputValue += ot.value;
        }

        return inputValue >= outputValue;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        
        if(possibleTxs.length==0)
            return new Transaction[0];
       
        HashSet<UTXO> usedOutputs = new HashSet<UTXO>();
        ArrayList<Transaction> newBlock = new ArrayList<Transaction>();
        HashSet<byte[]> txHashes = new HashSet<byte[]>();
        ArrayList<Transaction> laterTxs = new ArrayList<Transaction>();

        // save all tx hashes for quick lookup
        for(Transaction t: possibleTxs)
            txHashes.add(t.getHash());

        for(Transaction t: possibleTxs)
        {
            ArrayList<Transaction.Input> inputs = t.getInputs();
            boolean shouldAdd = true;
            boolean doLater = false;
            
            // check if any input has been used in this block
            for(Transaction.Input input: inputs)
            {
                UTXO curr = new UTXO(input.prevTxHash, input.outputIndex);
                if (usedOutputs.contains(curr)){
                    shouldAdd = false;
                    break;
                }
                if (txHashes.contains(input.prevTxHash))
                    doLater = true;
            }

            if(shouldAdd == false)
                continue;

            // if an input transaction is in possibleTxs, then save it for later and continue
            if(doLater)
            {
                laterTxs.add(t); 
                continue;
            }

            // add to new block if no double-spend and tx is valid
            if (isValidTx(t))
                newBlock.add(t);

            // add accepted transaction's inputs into usedOutputs
            for(Transaction.Input input: inputs)
            {
                UTXO curr = new UTXO(input.prevTxHash, input.outputIndex);
                usedOutputs.add(curr);
            }
        }

        //update utxoPool by removing old unspent outputs and adding new unused outputs
        for( Transaction t: newBlock)
        {
            //remove old unspent outputs
            ArrayList<Transaction.Input> inputs = t.getInputs();
            for(Transaction.Input input: inputs)
            {
                 UTXO curr = new UTXO(input.prevTxHash, input.outputIndex);
                 utxoPool.removeUTXO(curr);
            }

            //add new unspent outputs
            ArrayList<Transaction.Output> outputs = t.getOutputs();
            int newOutputIndex = 0;
            for(Transaction.Output output: outputs)
            {
                UTXO curr = new UTXO(t.getHash(), newOutputIndex);
                utxoPool.addUTXO(curr, output);
                newOutputIndex++;
            }
        }

        //recursively deal with txs that were saved for later using the new utxoPool
        Transaction[] addLast = handleTxs(laterTxs.toArray(new Transaction[laterTxs.size()]));
        for(Transaction t: addLast)
            newBlock.add(t);

        return newBlock.toArray(new Transaction[newBlock.size()]);
    }

}
