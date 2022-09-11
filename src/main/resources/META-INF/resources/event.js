function book(id) {
  const event = document.querySelector(`#event-${id}`);
  const button = event.querySelector(`#book-${id}`);
  button.classList.add('is-loading');
  fetch(`/events/${id}/tickets`, {
      method: 'POST',
      headers: {
        participant: uuid.v4()
      }
    })
    .then(response => {
      if (response.status === 202) {
        alert('Ticket successfully booked');
      } else if (response.status === 409) {
        alert('Unable to book a sold out event');
      } else {
        throw new Error(`Unable to book the ticket. The server returned ${response.status}`);
      }
    })
    .catch(error => {
      console.error(error);
      alert('Unable to book the ticket!')
    })
    .then(() => hydrate(id))
    .finally(() => button.classList.remove('is-loading'));
}

function hydrate(id) {
  eventAsElement(id)
    .then(hydrated => document.querySelector(`#event-${id}`).replaceWith(hydrated));
}

function eventAsElement(id) {
  return eventAsDocument(id)
    .then(document => document.querySelector(`#event-${id}`));
}

function eventAsDocument(id) {
  return fetch(`/events/${id}`, {
    headers: {
      accept: 'text/html'
    }
  })
  .then(response => {
    if (response.status === 200) {
      return response.text();
    }
    throw new Error(`Unable to fetch event. The server returned ${response.status}`)
  })
  .then(text => new DOMParser().parseFromString(text, 'text/html'))
}
