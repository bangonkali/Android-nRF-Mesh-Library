package no.nordicsemi.android.meshprovisioner.message;


import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import no.nordicsemi.android.meshprovisioner.message.type.AccessMessage;
import no.nordicsemi.android.meshprovisioner.message.type.ControlMessage;
import no.nordicsemi.android.meshprovisioner.message.type.Message;

/**
 * State class for handling unacknowledged GenericLevelSet messages.
 */
class GenericLevelSetUnacknowledgedState extends GenericMessageState {

    private static final String TAG = GenericLevelSetState.class.getSimpleName();

    /**
     * Constructs {@link GenericLevelSetUnacknowledgedState}
     *
     * @param context                Context of the application
     * @param dstAddress             Destination address to which the message must be sent to
     * @param genericLevelSetUnacked Wrapper class {@link GenericLevelSetUnacknowledged} containing the opcode and parameters for {@link GenericLevelSetUnacknowledged} message
     * @param callbacks              {@link InternalMeshMsgHandlerCallbacks} for internal callbacks
     * @throws IllegalArgumentException for any illegal arguments provided.
     */
    GenericLevelSetUnacknowledgedState(@NonNull final Context context,
                                              @NonNull final byte[] dstAddress,
                                              @NonNull final GenericLevelSetUnacknowledged genericLevelSetUnacked,
                                              @NonNull final MeshTransport meshTransport,
                                              @NonNull final InternalMeshMsgHandlerCallbacks callbacks) throws IllegalArgumentException {
        super(context, dstAddress, genericLevelSetUnacked, meshTransport, callbacks);
        createAccessMessage();
    }

    @Override
    public MessageState getState() {
        return MessageState.GENERIC_LEVEL_SET_STATE;
    }

    @Override
    public boolean parseMeshPdu(final byte[] pdu) {
        final Message message = mMeshTransport.parsePdu(mSrc, pdu);
        if (message != null) {
            if (message instanceof ControlMessage) {
                parseControlMessage((ControlMessage) message, mPayloads.size());
                return true;
            }
        } else {
            Log.v(TAG, "Message reassembly may not be complete yet");
        }
        return false;
    }

    /**
     * Creates the access message to be sent to the node
     */
    private void createAccessMessage() {
        final GenericLevelSetUnacknowledged genericLevelSet = (GenericLevelSetUnacknowledged) mMeshMessage;
        final byte[] key = genericLevelSet.getAppKey();
        final int akf = genericLevelSet.getAkf();
        final int aid = genericLevelSet.getAid();
        final int aszmic = genericLevelSet.getAszmic();
        final int opCode = genericLevelSet.getOpCode();
        final byte[] parameters = genericLevelSet.getParameters();
        message = mMeshTransport.createMeshMessage(mNode, mSrc, mDstAddress, key, akf, aid, aszmic, opCode, parameters);
        genericLevelSet.setMessage(message);
        mPayloads.putAll(message.getNetworkPdu());
    }

    @Override
    public final void executeSend() {
        Log.v(TAG, "Sending Generic Level set acknowledged ");
        super.executeSend();
    }
}