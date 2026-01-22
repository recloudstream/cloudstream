<script lang="ts">
  export let title = 'Confirm Action';
  export let message = 'Are you sure?';
  export let confirmText = 'Confirm';
  export let cancelText = 'Cancel';
  export let type: 'warning' | 'error' | 'info' = 'warning';

  let dialog: HTMLDialogElement;
  let onResolve: ((value: boolean) => void) | null = null;

  export function show(): Promise<boolean> {
      dialog.showModal();
      return new Promise((resolve) => {
          onResolve = resolve;
      });
  }

  function handleClose(result: boolean) {
      dialog.close();
      if (onResolve) {
          onResolve(result);
          onResolve = null;
      }
  }
</script>

<dialog bind:this={dialog} class="modal">
  <div class="modal-box">
      <h3 class="font-bold text-lg {type === 'error' ? 'text-error' : ''}">{title}</h3>
      <p class="py-4">{message}</p>
      <div class="modal-action">
          <button class="btn" onclick={() => handleClose(false)}>{cancelText}</button>
          <button 
              class="btn {type === 'error' ? 'btn-error' : 'btn-primary'}" 
              onclick={() => handleClose(true)}
          >
              {confirmText}
          </button>
      </div>
  </div>
  <form method="dialog" class="modal-backdrop">
      <button onclick={() => handleClose(false)}>close</button>
  </form>
</dialog>
