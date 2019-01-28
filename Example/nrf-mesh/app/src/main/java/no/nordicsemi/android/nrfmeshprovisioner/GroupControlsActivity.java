/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.nrfmeshprovisioner;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridLayout;
import android.widget.Switch;

import javax.inject.Inject;

import butterknife.ButterKnife;
import no.nordicsemi.android.meshprovisioner.Group;
import no.nordicsemi.android.meshprovisioner.models.SigModelParser;
import no.nordicsemi.android.meshprovisioner.transport.ApplicationKey;
import no.nordicsemi.android.meshprovisioner.transport.GenericLevelSetUnacknowledged;
import no.nordicsemi.android.meshprovisioner.transport.GenericOnOffSetUnacknowledged;
import no.nordicsemi.android.meshprovisioner.transport.MeshMessage;
import no.nordicsemi.android.meshprovisioner.utils.MeshParserUtils;
import no.nordicsemi.android.nrfmeshprovisioner.adapter.SubGroupAdapter;
import no.nordicsemi.android.nrfmeshprovisioner.di.Injectable;
import no.nordicsemi.android.nrfmeshprovisioner.utils.Utils;
import no.nordicsemi.android.nrfmeshprovisioner.viewmodels.GroupControlsViewModel;

public class GroupControlsActivity extends AppCompatActivity implements Injectable,
        SubGroupAdapter.OnItemClickListener,
        BottomSheetOnOffDialogFragment.BottomSheetOnOffListener,
        BottomSheetLevelDialogFragment.BottomSheetLevelListener {

    private GroupControlsViewModel mViewModel;
    private SubGroupAdapter groupAdapter;
    private RecyclerView recyclerViewSubGroups;
    @Inject
    ViewModelProvider.Factory mViewModelFactory;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_groups);
        ButterKnife.bind(this);
        mViewModel = ViewModelProviders.of(this, mViewModelFactory).get(GroupControlsViewModel.class);

        final Toolbar toolbar = findViewById(R.id.toolbar_info);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerViewSubGroups = findViewById(R.id.recycler_view_grouped_models);
        recyclerViewSubGroups.setLayoutManager(new LinearLayoutManager(this));
        groupAdapter = new SubGroupAdapter(this,
                mViewModel.getMeshManagerApi().getMeshNetwork(),
                mViewModel.getSelectedGroup(),
                mViewModel.isConnectedToProxy());
        groupAdapter.setOnItemClickListener(this);
        recyclerViewSubGroups.setAdapter(groupAdapter);

        mViewModel.getSelectedGroup().observe(this, group -> {
            getSupportActionBar().setTitle(group.getName());
            getSupportActionBar().setSubtitle(MeshParserUtils.bytesToHex(group.getGroupAddress(), true));
        });

        mViewModel.isConnectedToProxy().observe(this, aBoolean -> invalidateOptionsMenu());

    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (mViewModel.getProvisionedNodes().getValue() != null && !mViewModel.getProvisionedNodes().getValue().isEmpty()) {
            final Boolean isConnectedToNetwork = mViewModel.isConnectedToProxy().getValue();
            if (isConnectedToNetwork != null && isConnectedToNetwork) {
                getMenuInflater().inflate(R.menu.disconnect, menu);
            } else {
                getMenuInflater().inflate(R.menu.connect, menu);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_connect:
                final Intent intent = new Intent(this, ScannerActivity.class);
                intent.putExtra(Utils.EXTRA_DATA_PROVISIONING_SERVICE, false);
                startActivityForResult(intent, Utils.CONNECT_TO_NETWORK);
                return true;
            case R.id.action_disconnect:
                mViewModel.disconnect();
                return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSubGroupItemClick(final int appKeyIndex, final int modelId) {
        switch (modelId) {
            case SigModelParser.GENERIC_ON_OFF_SERVER:
                final BottomSheetOnOffDialogFragment onOffFragment = BottomSheetOnOffDialogFragment.getInstance(appKeyIndex);
                onOffFragment.show(getSupportFragmentManager(), "ON_OFF_FRAGMENT");
                break;
            case SigModelParser.GENERIC_LEVEL_SERVER:
                final BottomSheetLevelDialogFragment levelFragment = BottomSheetLevelDialogFragment.getInstance(appKeyIndex);
                levelFragment.show(getSupportFragmentManager(), "LEVEL_FRAGMENT");
                break;
        }
    }

    @Override
    public void toggle(final int appKeyIndex, final int modelId, final boolean isChecked) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final MeshMessage meshMessage;
        final ApplicationKey applicationKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(appKeyIndex);
        final int tid = mViewModel.getMeshManagerApi().getMeshNetwork().getSelectedProvisioner().getSequenceNumber();
        switch (modelId) {
            case SigModelParser.GENERIC_ON_OFF_SERVER:
                meshMessage = new GenericOnOffSetUnacknowledged(applicationKey.getKey(), isChecked, tid);
                mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);
                break;
            case SigModelParser.GENERIC_LEVEL_SERVER:
                meshMessage = new GenericLevelSetUnacknowledged(applicationKey.getKey(), isChecked ? 32678 : -32678, tid);
                mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);
                break;
        }

    }

    @Override
    public void toggle(final int keyIndex, final boolean state, final int transitionSteps, final int transitionStepResolution, final int delay) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final ApplicationKey applicationKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(keyIndex);
        final int tid = mViewModel.getMeshManagerApi().getMeshNetwork().getSelectedProvisioner().getSequenceNumber();
        final MeshMessage meshMessage = new GenericOnOffSetUnacknowledged(applicationKey.getKey(), state, tid, transitionSteps, transitionStepResolution, delay);
        mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);

        final RecyclerView.ViewHolder holder = recyclerViewSubGroups.findViewHolderForAdapterPosition(keyIndex);
        if(holder != null) {
            final GridLayout gridLayout = holder.itemView.findViewById(R.id.grp_grid);
            final View gridChild = gridLayout.findViewWithTag((int)SigModelParser.GENERIC_ON_OFF_SERVER);
            final Switch s = gridChild.findViewById(R.id.switch_on_off);
            s.setChecked(state);
        }
    }

    @Override
    public void toggleLevel(final int keyIndex, final int level, final int transitionSteps, final int transitionStepResolution, final int delay) {
        final Group group = mViewModel.getSelectedGroup().getValue();
        if (group == null)
            return;

        final ApplicationKey applicationKey = mViewModel.getMeshManagerApi().getMeshNetwork().getAppKey(keyIndex);
        final int tid = mViewModel.getMeshManagerApi().getMeshNetwork().getSelectedProvisioner().getSequenceNumber();
        final MeshMessage meshMessage = new GenericLevelSetUnacknowledged(applicationKey.getKey(), transitionSteps, transitionStepResolution, delay, level, tid);
        mViewModel.getMeshManagerApi().sendMeshMessage(group.getGroupAddress(), meshMessage);

        final RecyclerView.ViewHolder holder = recyclerViewSubGroups.findViewHolderForAdapterPosition(keyIndex);
        if(holder != null) {
            final GridLayout gridLayout = holder.itemView.findViewById(R.id.grp_grid);
            final View gridChild = gridLayout.findViewWithTag((int)SigModelParser.GENERIC_LEVEL_SERVER);
            final Switch s = gridChild.findViewById(R.id.switch_on_off);
            s.setChecked(level > -32768);
        }
    }
}