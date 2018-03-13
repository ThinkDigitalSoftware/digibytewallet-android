package io.digibyte.presenter.activities;

import static io.digibyte.tools.animation.BRAnimator.t1Size;
import static io.digibyte.tools.animation.BRAnimator.t2Size;
import static io.digibyte.tools.util.BRConstants.PLATFORM_ON;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.transition.TransitionManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.platform.APIClient;

import java.math.BigDecimal;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.digibyte.R;
import io.digibyte.presenter.activities.intro.WriteDownActivity;
import io.digibyte.presenter.activities.settings.FingerprintActivity;
import io.digibyte.presenter.activities.util.BRActivity;
import io.digibyte.presenter.customviews.BRSearchBar;
import io.digibyte.presenter.entities.TxItem;
import io.digibyte.presenter.fragments.FragmentManage;
import io.digibyte.tools.adapter.TransactionListAdapter;
import io.digibyte.tools.animation.BRAnimator;
import io.digibyte.tools.list.ListItemData;
import io.digibyte.tools.list.items.ListItemPromptData;
import io.digibyte.tools.list.items.ListItemPromptViewHolder;
import io.digibyte.tools.list.items.ListItemSyncingData;
import io.digibyte.tools.list.items.ListItemTransactionData;
import io.digibyte.tools.manager.BREventManager;
import io.digibyte.tools.manager.BRSharedPrefs;
import io.digibyte.tools.manager.InternetManager;
import io.digibyte.tools.manager.PromptManager;
import io.digibyte.tools.manager.SyncManager;
import io.digibyte.tools.manager.SyncService;
import io.digibyte.tools.manager.TxManager;
import io.digibyte.tools.manager.TxManager.onStatusListener;
import io.digibyte.tools.security.BitcoinUrlHandler;
import io.digibyte.tools.sqlite.TransactionDataSource;
import io.digibyte.tools.threads.BRExecutor;
import io.digibyte.tools.util.BRConstants;
import io.digibyte.tools.util.BRCurrency;
import io.digibyte.tools.util.BRExchange;
import io.digibyte.tools.util.Utils;
import io.digibyte.wallet.BRPeerManager;
import io.digibyte.wallet.BRWalletManager;

/**
 * BreadWallet
 * <p/>
 * Created by Mihail Gutan <mihail@breadwallet.com> on 8/4/15.
 * Copyright (c) 2016 breadwallet LLC
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

public class BreadActivity extends BRActivity implements BRWalletManager.OnBalanceChanged, BRPeerManager.OnTxStatusUpdate, BRSharedPrefs.OnIsoChangedListener, TransactionDataSource.OnTxAddedListener, InternetManager.ConnectionReceiverListener, SyncManager.onStatusListener, onStatusListener
{
    private final int LIST_SECTION_INFORMATION = 0;
    private final int LIST_SECTION_TRANSACTIONS = 1;

    @BindView(R.id.send_layout)
    LinearLayout sendButton;

    @BindView(R.id.receive_layout)
    LinearLayout receiveButton;

    @BindView(R.id.manage_text)
    TextView manageText;

    @BindView(R.id.menu_layout)
    LinearLayout menuButton;

    @BindView(R.id.primary_price)
    TextView primaryPrice;

    @BindView(R.id.secondary_price)
    TextView secondaryPrice;

    @BindView(R.id.equals)
    TextView equals;

    @BindView(R.id.toolbar_layout)
    LinearLayout toolbarLayout;

    @BindView(R.id.search_icon)
    ImageButton searchIcon;

    @BindView(R.id.search_bar)
    BRSearchBar searchBar;

    @BindView(R.id.tool_bar_flipper)
    public ViewFlipper barFlipper;

    @BindView(R.id.bread_toolbar)
    ConstraintLayout toolBarConstraintLayout;

    @BindView(R.id.tx_list)
    RecyclerView listView;

    private InternetManager mConnectionReceiver = InternetManager.getInstance();
    private ListItemSyncingData listItemSyncingData = new ListItemSyncingData();
    private TransactionListAdapter listViewAdapter = new TransactionListAdapter();;
    private Handler handler = new Handler(Looper.getMainLooper());
    private ArrayList<ListItemData> informationList = new ArrayList<>();
    private Unbinder unbinder;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bread);
        unbinder = ButterKnife.bind(this);

        initializeViews();
        setupInformationListPromptSwipe();

        onConnectionChanged(InternetManager.getInstance().isConnected(this));

        oneTimeGreeting();
        updateUI();
        loadNextPropmptItem();
    }

    private void oneTimeGreeting() {
        if (!BRSharedPrefs.getGreetingsShown(BreadActivity.this))
        {
            handler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    BRAnimator.showGreetingsMessage(BreadActivity.this);
                    BRSharedPrefs.putGreetingsShown(BreadActivity.this, true);
                }
            }, 1000);
        }
    }

    private void initializeViews()
    {
        // Set listeners
        sendButton.setOnClickListener(this.onButtonSend);
        receiveButton.setOnClickListener(this.onButtonReceive);
        menuButton.setOnClickListener(this.onButtonMenu);
        manageText.setOnClickListener(this.onManageText);
        primaryPrice.setOnClickListener(this.onButtonPrice);
        secondaryPrice.setOnClickListener(this.onButtonPrice);
        searchIcon.setOnClickListener(this.onButtonSearch);

        // Setup list view
        listView.setItemAnimator(null);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listViewAdapter.addSection(LIST_SECTION_INFORMATION);
        listViewAdapter.addSection(LIST_SECTION_TRANSACTIONS);
        listView.setAdapter(listViewAdapter);
        primaryPrice.setTextSize(TypedValue.COMPLEX_UNIT_PX, t1Size);//make it the size it should be after animation to get the X
        secondaryPrice.setTextSize(TypedValue.COMPLEX_UNIT_PX, t2Size);//make it the size it should be after animation to get the X
        handler.post(new Runnable() {
            @Override
            public void run() {
                setPriceTags(BRSharedPrefs.getPreferredBTC(BreadActivity.this), false);
            }
        });
    }

    private void updateUI()
    {
        handler.post(new Runnable() {
            @Override
            public void run() {
                //sleep a little in order to make sure all the commits are finished (like SharePreferences commits)
                final String iso = BRSharedPrefs.getIso(BreadActivity.this);

                //current amount in satoshis
                final BigDecimal amount = new BigDecimal(BRSharedPrefs.getCatchedBalance(BreadActivity.this));

                //amount in BTC units
                final BigDecimal btcAmount = BRExchange.getBitcoinForSatoshis(BreadActivity.this, amount);
                final String formattedBTCAmount = BRCurrency.getFormattedCurrencyString(BreadActivity.this, "DGB", btcAmount);

                //amount in currency units
                final BigDecimal curAmount = BRExchange.getAmountFromSatoshis(BreadActivity.this, iso, amount);
                final String formattedCurAmount = BRCurrency.getFormattedCurrencyString(BreadActivity.this, iso, curAmount);
                primaryPrice.setText(formattedBTCAmount);
                secondaryPrice.setText(String.format("%s", formattedCurAmount));

                TxManager.getInstance().updateTxList();
            }
        });
    }

    private void loadNextPropmptItem()
    {
        PromptManager.PromptItem promptItem = PromptManager.getInstance().nextPrompt();
        if (null != promptItem)
        {
            informationList.add(new ListItemPromptData(promptItem, onPromptListItemClick, onPromptListItemCloseClick));
            BREventManager.getInstance().pushEvent("prompt." + PromptManager.getInstance().getPromptName(promptItem) + ".displayed");
            listViewAdapter.addItemsInSection(LIST_SECTION_INFORMATION, informationList);
        }
    }

    private void setupInformationListPromptSwipe()
    {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT)
        {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target)
            {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir)
            {
                if (viewHolder instanceof ListItemPromptViewHolder)
                {
                    ListItemPromptViewHolder listItemViewHolder = (ListItemPromptViewHolder) viewHolder;
                    onPromptListItemCloseClick.onListItemClick(listItemViewHolder.getItemData());
                }
            }

            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder)
            {
                if (viewHolder instanceof ListItemPromptViewHolder)
                {
                    return super.getSwipeDirs(recyclerView, viewHolder);
                }
                return 0;
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(listView);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////// List item click listeners ////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private ListItemData.OnListItemClickListener onTransactionListItemClick = new ListItemData.OnListItemClickListener()
    {
        @Override
        public void onListItemClick(ListItemData aListItemData)
        {
            int position = 0;
            ArrayList<TxItem> transactionItems = new ArrayList<>();
            ArrayList<ListItemData> transactionList = listViewAdapter.getItemsInSection(LIST_SECTION_TRANSACTIONS);
            for(int index = 0; index < transactionList.size(); index++)
            {
                ListItemTransactionData listItem = (ListItemTransactionData) transactionList.get(index);
                if(listItem.equals(aListItemData))
                {
                    position = index;
                }
                transactionItems.add(listItem.transactionItem);
            }
            BRAnimator.showTransactionPager(BreadActivity.this, transactionItems, position);
        }
    };

    private ListItemData.OnListItemClickListener onPromptListItemClick = new ListItemData.OnListItemClickListener()
    {
        @Override
        public void onListItemClick(ListItemData aListItemData)
        {
            Intent intent;
            final Activity activity = BreadActivity.this;
            ListItemPromptData data = (ListItemPromptData) aListItemData;

            BREventManager.getInstance().pushEvent("prompt." + PromptManager.getInstance().getPromptName(data.promptItem) + ".trigger");

            switch (data.promptItem)
            {
                case SYNCING:
                    break;
                case FINGER_PRINT:
                    intent = new Intent(activity, FingerprintActivity.class);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                    break;
                case PAPER_KEY:
                    intent = new Intent(activity, WriteDownActivity.class);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(R.anim.enter_from_bottom, R.anim.fade_down);
                    break;
                case UPGRADE_PIN:
                    intent = new Intent(activity, UpdatePinActivity.class);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
                    break;
                case RECOMMEND_RESCAN:
                    BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            BRSharedPrefs.putStartHeight(activity, 0);
                            BRPeerManager.getInstance().rescan();
                            BRSharedPrefs.putScanRecommended(activity, false);
                        }
                    });
                    break;
                case NO_PASS_CODE:
                    break;
            }
        }
    };

    private ListItemData.OnListItemClickListener onPromptListItemCloseClick = new ListItemData.OnListItemClickListener()
    {
        @Override
        public void onListItemClick(ListItemData aListItemData)
        {
            ListItemPromptData data = (ListItemPromptData) aListItemData;
            informationList.remove(data);
            listViewAdapter.removeItemInSection(LIST_SECTION_INFORMATION, data);
            BREventManager.getInstance().pushEvent("prompt." + PromptManager.getInstance().getPromptName(data.promptItem) + ".dismissed");
            loadNextPropmptItem();
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////// Manager Listeners ////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onSyncManagerStart()
    {
        informationList.clear();
        informationList.add(listItemSyncingData);
        listViewAdapter.addItemsInSection(LIST_SECTION_INFORMATION, informationList);
    }

    public void onSyncManagerUpdate()
    {
        if (!informationList.contains(listItemSyncingData)) {
            informationList.clear();
            informationList.add(listItemSyncingData);
            listViewAdapter.addItemsInSection(LIST_SECTION_INFORMATION, informationList);
        }
        listViewAdapter.updateSection(LIST_SECTION_INFORMATION);
    }

    @Override
    public void onSyncManagerFinished()
    {
        listViewAdapter.removeItemInSection(LIST_SECTION_INFORMATION, listItemSyncingData);
        informationList.clear();
        loadNextPropmptItem();
    }

    @Override
    public void onTxManagerUpdate(TxItem[] aTransactionList)
    {
        if (null != aTransactionList)
        {
            ArrayList<ListItemData> transactionList = new ArrayList<>();

            int transactionsCount = aTransactionList.length;
            for (int index = 0; index < transactionsCount; index++)
            {
                transactionList.add(new ListItemTransactionData(index, transactionsCount, aTransactionList[index], onTransactionListItemClick));
            }
            listViewAdapter.addItemsInSection(LIST_SECTION_TRANSACTIONS, transactionList);
        }
    }

    @Override
    public void onStatusUpdate()
    {
        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable()
        {
            @Override
            public void run()
            {
                TxManager.getInstance().updateTxList();
            }
        });
    }

    @Override
    public void onIsoChanged(String iso)
    {
        updateUI();
    }

    @Override
    public void onTxAdded()
    {
        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable()
        {
            @Override
            public void run()
            {
                TxManager.getInstance().updateTxList();
            }
        });
        BRWalletManager.getInstance().refreshBalance(BreadActivity.this);
    }

    @Override
    public void onConnectionChanged(boolean isConnected)
    {
        if (isConnected)
        {
            if (barFlipper.getDisplayedChild() == 2)
            {
                barFlipper.setDisplayedChild(0);
            }

            BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable()
            {
                @Override
                public void run()
                {
                    final double progress = BRPeerManager.syncProgress(BRSharedPrefs.getStartHeight(BreadActivity.this));
                    if (progress < 1 && progress > 0)
                    {
                        SyncManager.getInstance().startSyncingProgressThread();
                    }
                }
            });
        }
        else
        {
            barFlipper.setDisplayedChild(2);
            SyncManager.getInstance().stopSyncingProgressThread();
        }
    }

    @Override
    public void onBalanceChanged(final long balance)
    {
        updateUI();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////// UI OnClick Listeners /////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private View.OnClickListener onButtonSend = new View.OnClickListener()
    {
        @Override
        public void onClick(View aView)
        {
            if (BRAnimator.isClickAllowed())
            {
                BRAnimator.showSendFragment(BreadActivity.this, null);
            }
        }
    };

    private View.OnClickListener onButtonReceive = new View.OnClickListener()
    {
        @Override
        public void onClick(View aView)
        {
            if (BRAnimator.isClickAllowed())
            {
                BRAnimator.showReceiveFragment(BreadActivity.this, true);
            }
        }
    };

    private View.OnClickListener onButtonMenu = new View.OnClickListener()
    {
        @Override
        public void onClick(View aView)
        {
            if (BRAnimator.isClickAllowed())
            {
                BRAnimator.showMenuFragment(BreadActivity.this);
            }
        }
    };

    private View.OnClickListener onManageText = new View.OnClickListener()
    {
        @Override
        public void onClick(View aView)
        {
            if (BRAnimator.isClickAllowed())
            {
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.setCustomAnimations(0, 0, 0, R.animator.plain_300);
                FragmentManage fragmentManage = new FragmentManage();
                transaction.add(android.R.id.content, fragmentManage, FragmentManage.class.getName());
                transaction.addToBackStack(null);
                transaction.commit();
            }
        }
    };

    private View.OnClickListener onButtonPrice = new View.OnClickListener()
    {
        @Override
        public void onClick(View aView)
        {
            if (BRAnimator.isClickAllowed())
            {
                boolean b = !BRSharedPrefs.getPreferredBTC(BreadActivity.this);
                setPriceTags(b, true);
                BRSharedPrefs.putPreferredBTC(BreadActivity.this, b);
            }
        }
    };

    private View.OnClickListener onButtonSearch = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            if (BRAnimator.isClickAllowed())
            {
                barFlipper.setDisplayedChild(1); //search bar
                searchBar.onShow(true);
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////// LEFT OVERS CLEANUP? //////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private void setPriceTags(boolean btcPreferred, boolean animate)
    {
        secondaryPrice.setTextSize(!btcPreferred ? t1Size : t2Size);
        primaryPrice.setTextSize(!btcPreferred ? t2Size : t1Size);
        ConstraintSet set = new ConstraintSet();
        set.clone(toolBarConstraintLayout);
        if (animate)
        {
            TransitionManager.beginDelayedTransition(toolBarConstraintLayout);
        }
        int px4 = Utils.getPixelsFromDps(this, 4);
        int px16 = Utils.getPixelsFromDps(this, 16);
        //align to parent left
        set.connect(!btcPreferred ? R.id.secondary_price : R.id.primary_price, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.END, px16);
        //align equals after the first item
        set.connect(R.id.equals, ConstraintSet.START, !btcPreferred ? secondaryPrice.getId() : primaryPrice.getId(), ConstraintSet.END, px4);
        //align second item after equals
        set.connect(!btcPreferred ? R.id.primary_price : R.id.secondary_price, ConstraintSet.START, equals.getId(), ConstraintSet.END, px4);
        //        align the second item to the baseline of the first
        //        set.connect(!btcPreferred ? R.id.primary_price : R.id.secondary_price, ConstraintSet.BASELINE, btcPreferred ? R.id.primary_price : R.id.secondary_price, ConstraintSet.BASELINE, 0);
        // Apply the changes
        set.applyTo(toolBarConstraintLayout);

        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                updateUI();
            }
        }, toolBarConstraintLayout.getLayoutTransition().getDuration(LayoutTransition.CHANGING));
    }

    //returns x-pos relative to root layout
    private float getRelativeX(View myView)
    {
        if (myView.getParent() == myView.getRootView())
        {
            return myView.getX();
        }
        else
        {
            return myView.getX() + getRelativeX((View) myView.getParent());
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////// Activity overrides ///////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        //leave it empty because the FragmentMenu is improperly designed
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        Uri data = intent.getData();
        if (data != null)
        {
            String scheme = data.getScheme();
            if (scheme != null && (scheme.startsWith("digibyte") || scheme.startsWith("digiid")))
            {
                BitcoinUrlHandler.processRequest(this, intent.getDataString());
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (PLATFORM_ON)
        {
            APIClient.getInstance(this).updatePlatform();
        }

        IntentFilter mNetworkStateFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectionReceiver, mNetworkStateFilter);
        InternetManager.addConnectionListener(this);

        if (!BRWalletManager.getInstance().isCreated())
        {
            BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable()
            {
                @Override
                public void run()
                {
                    BRWalletManager.getInstance().initWallet(BreadActivity.this);
                }
            });
        }
        handler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                updateUI();
            }
        }, 1000);

        BRWalletManager.getInstance().addBalanceChangedListener(this);
        BRPeerManager.getInstance().addStatusUpdateListener(this);
        BRSharedPrefs.addIsoChangedListener(this);

        TxManager.getInstance().addListener(this);
        SyncManager.getInstance().addListener(this);

        BRWalletManager.getInstance().refreshBalance(this);
        SyncService.scheduleBackgroundSync(this);
        TxManager.getInstance().onResume(BreadActivity.this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(mConnectionReceiver);
        InternetManager.removeConnectionListener(this);

        BRWalletManager.getInstance().removeListener(this);
        BRPeerManager.getInstance().removeListener(this);
        BRSharedPrefs.removeListener(this);

        TxManager.getInstance().removeListener(this);
        SyncManager.getInstance().removeListener(this);

        //sync the kv stores
        if (PLATFORM_ON)
        {
            BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable()
            {
                @Override
                public void run()
                {
                    APIClient.getInstance(BreadActivity.this).syncKvStore();
                }
            });
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unbinder.unbind();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode)
        {
            case BRConstants.CAMERA_REQUEST_ID:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    BRAnimator.openScanner(this, BRConstants.SCANNER_REQUEST);
                }
                break;
            }
        }
    }
}