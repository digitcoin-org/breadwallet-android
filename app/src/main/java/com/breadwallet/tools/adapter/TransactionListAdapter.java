package com.breadwallet.tools.adapter;

import android.app.Activity;
import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.breadwallet.R;
import com.breadwallet.core.ethereum.BREthereumTransaction;
import com.breadwallet.presenter.customviews.BRText;
import com.breadwallet.presenter.entities.TxUiHolder;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.BRDateUtil;
import com.breadwallet.tools.util.CurrencyUtils;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.platform.entities.TxMetaData;
import com.platform.tools.KVStoreManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * BreadWallet
 * <p>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 7/27/15.
 * Copyright (c) 2016 breadwallet LLC
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class TransactionListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final String TAG = TransactionListAdapter.class.getName();

    private final Context mContext;
    private final int txResId;
    private final int promptResId;
    private List<TxUiHolder> backUpFeed;
    private List<TxUiHolder> itemFeed;
    private Map<Integer, TxMetaData> mMetaDatas;

    private final int txType = 0;
    private final int promptType = 1;
    private boolean updatingData;


    public TransactionListAdapter(Context mContext, List<TxUiHolder> items) {
        this.txResId = R.layout.tx_item;
        this.promptResId = R.layout.prompt_item;
        this.mContext = mContext;
        backUpFeed = items;
        itemFeed = items;
        mMetaDatas = new HashMap<>();
        items = new ArrayList<>();
        init(items);
//        updateMetadata();
    }

    public void setItems(List<TxUiHolder> items) {
        init(items);
    }

    private void init(List<TxUiHolder> items) {
        if (items == null) items = new ArrayList<>();
        if (itemFeed == null) itemFeed = new ArrayList<>();
        if (backUpFeed == null) backUpFeed = new ArrayList<>();
        this.itemFeed = items;
        this.backUpFeed = items;

    }

    public void updateData() {
        if (updatingData) return;
        Map<Integer, TxMetaData> localMDs = new HashMap<>();
        for (int i = 0; i < backUpFeed.size(); i++) {
            TxUiHolder item = backUpFeed.get(i);
            localMDs.put(i, KVStoreManager.getInstance().getTxMetaData(mContext, item.getTxHash()));

        }
        mMetaDatas.clear();
        mMetaDatas.putAll(localMDs);
        updatingData = false;
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });

    }

    public List<TxUiHolder> getItems() {
        return itemFeed;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // inflate the layout
        LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
        return new TxHolder(inflater.inflate(txResId, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case txType:
                holder.setIsRecyclable(false);
                setTexts((TxHolder) holder, position);
                break;
            case promptType:
                //setPrompt((PromptHolder) holder);
                break;
        }

    }

    @Override
    public int getItemViewType(int position) {
        return txType;
    }

    @Override
    public int getItemCount() {
        return itemFeed.size();
    }

    private void setTexts(final TxHolder convertView, int position) {
        BaseWalletManager wallet = WalletsMaster.getInstance(mContext).getCurrentWallet(mContext);
        TxUiHolder item = itemFeed.get(position);
//        item.metaData = KVStoreManager.getInstance().getTxMetaData(mContext, item.getTxHash());

        String commentString = "";
        TxMetaData md = mMetaDatas.size() > position ? mMetaDatas.get(position) : null;
        if (md != null) {
            if (md.comment != null) {
                commentString = md.comment;
            }
        }

        boolean received = item.isReceived();

        convertView.transactionAmount.setTextColor(mContext.getResources().getColor(received ?
                R.color.transaction_amount_received_color : R.color.total_assets_usd_color, null));

        // If this transaction failed, show the "FAILED" indicator in the cell
        if (!item.isValid())
            showTransactionFailed(convertView, item, received);

        BigDecimal cryptoAmount = item.getAmount().abs();
        boolean isCryptoPreferred = BRSharedPrefs.isCryptoPreferred(mContext);
        String preferredIso = isCryptoPreferred ? wallet.getIso(mContext) : BRSharedPrefs.getPreferredFiatIso(mContext);

        BigDecimal amount = isCryptoPreferred ? cryptoAmount : wallet.getFiatForSmallestCrypto(mContext, cryptoAmount, null);
        convertView.transactionAmount.setText(CurrencyUtils.getFormattedAmount(mContext, preferredIso, received ? amount : (amount == null ? null : amount.negate())));

        int blockHeight = item.getBlockHeight();
        int lastBlockHeight = BRSharedPrefs.getLastBlockHeight(mContext, wallet.getIso(mContext));
        int confirms = blockHeight == Integer.MAX_VALUE ? 0 : lastBlockHeight - blockHeight + 1;

        int level;
        if (confirms <= 0) {
            long relayCount = wallet.getRelayCount(item.getTxHash());
            if (relayCount <= 0)
                level = 0;
            else if (relayCount == 1)
                level = 1;
            else
                level = 2;
        } else {
            if (confirms == 1)
                level = 3;
            else if (confirms == 2)
                level = 4;
            else if (confirms == 3)
                level = 5;
            else
                level = 6;
        }
        if (level > 0 && level < 5)
            showTransactionProgress(convertView, level * 20);

        String sentTo = String.format(mContext.getString(R.string.Transaction_sentTo), wallet.decorateAddress(mContext, item.getTo()));
        String receivedVia = String.format(mContext.getString(R.string.TransactionDetails_receivedVia), wallet.decorateAddress(mContext, item.getTo()));

        String sendingTo = String.format(mContext.getString(R.string.Transaction_sendingTo), wallet.decorateAddress(mContext, item.getTo()));
        String receivingVia = String.format(mContext.getString(R.string.TransactionDetails_receivingVia), wallet.decorateAddress(mContext, item.getTo()));

        if (level > 4) {
            convertView.transactionDetail.setText(!commentString.isEmpty() ? commentString : (!received ? sentTo : receivedVia));
        } else {
            convertView.transactionDetail.setText(!commentString.isEmpty() ? commentString : (!received ? sendingTo : receivingVia));

        }

        //if it's 0 we use the current time.
        long timeStamp = item.getTimeStamp() == 0 ? System.currentTimeMillis() : item.getTimeStamp() * 1000;

        String shortDate = BRDateUtil.getShortDate(timeStamp);

        convertView.transactionDate.setText(shortDate);
    }

    private void showTransactionProgress(TxHolder holder, int progress) {
        if (progress < 100) {
            holder.transactionProgress.setVisibility(View.VISIBLE);
            holder.transactionDate.setVisibility(View.GONE);
            holder.transactionProgress.setProgress(progress);

            RelativeLayout.LayoutParams detailParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            detailParams.addRule(RelativeLayout.RIGHT_OF, holder.transactionProgress.getId());
            detailParams.addRule(RelativeLayout.CENTER_VERTICAL);
            detailParams.setMargins(Utils.getPixelsFromDps(mContext, 16), Utils.getPixelsFromDps(mContext, 36), 0, 0);
            holder.transactionDetail.setLayoutParams(detailParams);
        } else {
            holder.transactionProgress.setVisibility(View.INVISIBLE);
            holder.transactionDate.setVisibility(View.VISIBLE);
            RelativeLayout.LayoutParams startingParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            startingParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            startingParams.addRule(RelativeLayout.CENTER_VERTICAL);
            startingParams.setMargins(Utils.getPixelsFromDps(mContext, 16), 0, 0, 0);
            holder.transactionDetail.setLayoutParams(startingParams);
            holder.setIsRecyclable(true);
        }
    }

    private void showTransactionFailed(TxHolder holder, TxUiHolder tx, boolean received) {

        holder.transactionDate.setVisibility(View.INVISIBLE);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.RIGHT_OF, holder.transactionFailed.getId());
        params.setMargins(16, 0, 0, 0);
        params.addRule(RelativeLayout.CENTER_VERTICAL, holder.transactionFailed.getId());
        holder.transactionDetail.setLayoutParams(params);
        BaseWalletManager wm = WalletsMaster.getInstance(mContext).getCurrentWallet(mContext);

        if (!received)
            holder.transactionDetail.setText(String.format(mContext.getString(R.string.Transaction_sendingTo), wm.decorateAddress(mContext, tx.getTo())));

    }

    public void filterBy(String query, boolean[] switches) {
        filter(query, switches);
    }

    public void resetFilter() {
        itemFeed = backUpFeed;
        notifyDataSetChanged();
    }

    private void filter(final String query, final boolean[] switches) {
        long start = System.currentTimeMillis();
        String lowerQuery = query.toLowerCase().trim();
        if (Utils.isNullOrEmpty(lowerQuery) && !switches[0] && !switches[1] && !switches[2] && !switches[3])
            return;
        int switchesON = 0;
        for (boolean i : switches) if (i) switchesON++;

        final List<TxUiHolder> filteredList = new ArrayList<>();
        TxUiHolder item;
        for (int i = 0; i < backUpFeed.size(); i++) {
            item = backUpFeed.get(i);
            boolean matchesHash = item.getHashReversed() != null && item.getHashReversed().contains(lowerQuery);
            boolean matchesAddress = item.getFrom().contains(lowerQuery) || item.getTo().contains(lowerQuery);
            boolean matchesMemo = item.metaData != null && item.metaData.comment != null && item.metaData.comment.toLowerCase().contains(lowerQuery);
            if (matchesHash || matchesAddress || matchesMemo) {
                if (switchesON == 0) {
                    filteredList.add(item);
                } else {
                    boolean willAdd = true;
                    //filter by sent and this is received
                    if (switches[0] && item.isReceived()) {
                        willAdd = false;
                    }
                    //filter by received and this is sent
                    if (switches[1] && !item.isReceived()) {
                        willAdd = false;
                    }
                    BaseWalletManager wallet = WalletsMaster.getInstance(mContext).getCurrentWallet(mContext);

                    int confirms = item.getBlockHeight() == Integer.MAX_VALUE ? 0
                            : BRSharedPrefs.getLastBlockHeight(mContext, wallet.getIso(mContext)) - item.getBlockHeight() + 1;
                    //complete
                    if (switches[2] && confirms >= 6) {
                        willAdd = false;
                    }

                    //pending
                    if (switches[3] && confirms < 6) {
                        willAdd = false;
                    }

                    if (willAdd) filteredList.add(item);
                }

            }

        }
        itemFeed = filteredList;
        notifyDataSetChanged();

//        Log.e(TAG, "filter: " + query + " took: " + (System.currentTimeMillis() - start));
    }

    private class TxHolder extends RecyclerView.ViewHolder {
        public RelativeLayout mainLayout;
        public ConstraintLayout constraintLayout;
        public TextView sentReceived;
        public TextView amount;
        public TextView account;
        public TextView status;
        public TextView status_2;
        public TextView timestamp;
        public TextView comment;
        public ImageView arrowIcon;

        public BRText transactionDate;
        public BRText transactionAmount;
        public BRText transactionDetail;
        public Button transactionFailed;
        public ProgressBar transactionProgress;


        public TxHolder(View view) {
            super(view);

            transactionDate = view.findViewById(R.id.tx_date);
            transactionAmount = view.findViewById(R.id.tx_amount);
            transactionDetail = view.findViewById(R.id.tx_description);
            transactionFailed = view.findViewById(R.id.tx_failed_button);
            transactionProgress = view.findViewById(R.id.tx_progress);

        }
    }

}