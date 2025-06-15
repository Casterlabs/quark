package co.casterlabs.quark.session;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;

class _CodecsSessionListener extends QuarkSessionListener {

    private void process(FLVTag tag) {
        if (tag.type() == FLVTagType.SCRIPT) return; // ignore.

    }

    @Override
    public void onSequence(QuarkSession session, FLVSequence seq) {
        for (FLVTag tag : seq.tags()) {
            this.process(tag);
        }
    }

    @Override
    public void onData(QuarkSession session, FLVData data) {
        this.process(data.tag());
    }

    @Override
    public void onClose(QuarkSession session) {} // NOOP

    @Override
    public boolean async() {
        return false;
    }

}
