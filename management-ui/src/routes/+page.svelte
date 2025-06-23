<script lang="ts">
	import { addInstance, instances, removeInstance } from '$lib/config';

	import Card from '$lib/layout/Card.svelte';
	import Modal from '$lib/layout/Modal.svelte';
	import { IconHeart } from '@casterlabs/heroicons-svelte';
	import { Button, Input } from '@casterlabs/ui';

	let showInstanceAddModal = $state(false);
	let addInstanceId = $state('');
	let addInstanceAddress = $state('');
	let addInstanceToken = $state('');
</script>

<div class="h-screen flex items-center justify-center">
	<ul class="max-w-2xl justify-center flex flex-wrap">
		{#each instances() as instance}
			<li>
				<Card href="/instance/{encodeURI(instance.id)}">
					<span class="truncate">{instance.id} <span class="text-xs text-base-11">{instance._address}</span></span>
					<br />
					{#await instance.healthCheck()}
						<span class="text-base-9 inline-block">
							<IconHeart class="inline align-bottom" theme="solid"></IconHeart>
						</span>
					{:then isOk}
						<span class="inline-block" class:text-green-400={isOk} class:text-red-400={!isOk}>
							<IconHeart class="inline align-bottom" theme="solid"></IconHeart>
						</span>
					{/await}
					<Button
						onclick={() => {
							removeInstance(instance.id);
						}}
					>
						Remove
					</Button>
				</Card>
			</li>
		{/each}

		<li>
			<Card onclick={() => (showInstanceAddModal = true)}>
				<div class=" w-full h-full flex items-center justify-center">
					<span class="sr-only"> Add Instance </span>
					<span aria-hidden="true"> + </span>
				</div>
			</Card>
		</li>
	</ul>
</div>

<Modal bind:showModal={showInstanceAddModal}>
	<h1 class="font-semibold text-lg">Add Instance</h1>
	<div class="space-y-1">
		<Input type="text" placeholder="Instance Name" bind:value={addInstanceId} class="w-full px-1 py-0.5" />
		<Input type="text" placeholder="Address" bind:value={addInstanceAddress} class="w-full px-1 py-0.5" />
		<Input type="password" placeholder="Token" bind:value={addInstanceToken} class="w-full px-1 py-0.5" />
	</div>
	<Button
		class="px-1 w-full"
		onclick={() => {
			addInstance(addInstanceId, addInstanceAddress, addInstanceToken);
		}}
	>
		Add
	</Button>
</Modal>
